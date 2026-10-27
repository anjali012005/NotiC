"use client";

import { FormEvent, useEffect, useState } from "react";
import { useApiKey } from "@/components/api-key-context";
import { EmptyState, ErrorNotice, LoadingState, PageTitle } from "@/components/ui";
import { ApiError, createTemplate, deleteTemplate, getTemplateVersions, getTemplates, updateTemplate } from "@/lib/api";
import type { Channel, Template, TemplateVersion } from "@/types/api";

const channels: Channel[] = ["EMAIL", "SMS", "PUSH", "WHATSAPP"];
type TemplateFields = { templateKey: string; name: string; channel: Channel; subject: string; body: string; tags: string };
const blankFields: TemplateFields = { templateKey: "", name: "", channel: "EMAIL", subject: "", body: "", tags: "" };

function formatDate(value: string) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "Unavailable" : date.toLocaleDateString();
}

export default function TemplatesPage() {
    const { apiKey } = useApiKey();
    const [items, setItems] = useState<Template[]>([]);
    const [selected, setSelected] = useState<Template | null>(null);
    const [versions, setVersions] = useState<TemplateVersion[] | null>(null);
    const [fields, setFields] = useState<TemplateFields>(blankFields);
    const [editing, setEditing] = useState(false);
    const [creating, setCreating] = useState(false);
    const [channel, setChannel] = useState("");
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [last, setLast] = useState(true);
    const [revision, setRevision] = useState(0);
    const [loading, setLoading] = useState(true);
    const [pending, setPending] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
    const [feedback, setFeedback] = useState<string | null>(null);

    useEffect(() => {
        if (!apiKey) return;
        let active = true;
        setLoading(true);
        setItems([]);
        setError(null);
        getTemplates(apiKey, page, channel || undefined).then((response) => {
            if (!active) return;
            setItems(response.content);
            setTotalPages(response.totalPages);
            setLast(response.last);
            if (selected && !response.content.some((item) => item.id === selected.id)) {
                setSelected(null);
                setVersions(null);
            }
        }).catch((cause: unknown) => {
            if (active) setError(cause instanceof ApiError ? cause.message : "Templates could not be loaded.");
        }).finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [apiKey, page, channel, revision]);

    function startEdit(template: Template) {
        setSelected(template);
        setVersions(null);
        setCreating(false);
        setEditing(true);
        setFeedback(null);
        setFields({ templateKey: template.templateKey, name: template.name, channel: template.channel, subject: template.subject, body: template.body, tags: (template.tags ?? []).join(", ") });
    }

    function startCreate() {
        setSelected(null);
        setVersions(null);
        setEditing(false);
        setCreating(true);
        setFeedback(null);
        setFields(blankFields);
    }

    async function saveTemplate(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError(null);
        setFieldErrors({});
        if (!fields.name.trim() || !fields.subject.trim() || !fields.body.trim() || (creating && !fields.templateKey.trim())) {
            setFieldErrors({ required: "Complete every required field before saving." });
            return;
        }
        if (!apiKey) return;
        setPending(true);
        const tags = fields.tags.split(",").map((tag) => tag.trim()).filter(Boolean);
        try {
            if (creating) {
                const saved = await createTemplate(apiKey, { templateKey: fields.templateKey.trim(), name: fields.name.trim(), channel: fields.channel, subject: fields.subject.trim(), body: fields.body, tags });
                setFeedback(`Template ${saved.name} created.`);
            } else if (selected) {
                const saved = await updateTemplate(apiKey, selected.id, { name: fields.name.trim(), channel: fields.channel, subject: fields.subject.trim(), body: fields.body, tags });
                setFeedback(`Template ${saved.name} updated and a new immutable version was created.`);
            }
            setCreating(false);
            setEditing(false);
            setRevision((value) => value + 1);
        } catch (cause) {
            if (cause instanceof ApiError) {
                setError(cause.message);
                setFieldErrors(cause.fieldErrors);
            } else setError("The template could not be saved.");
        } finally {
            setPending(false);
        }
    }

    async function inspectVersions(template: Template) {
        if (!apiKey) return;
        setSelected(template);
        setEditing(false);
        setCreating(false);
        setVersions(null);
        setError(null);
        try {
            setVersions(await getTemplateVersions(apiKey, template.id));
        } catch (cause) {
            setError(cause instanceof ApiError ? cause.message : "Version history could not be loaded.");
        }
    }

    async function removeTemplate(template: Template) {
        if (!apiKey || !window.confirm(`Delete template “${template.name}”? Existing notifications are not deleted.`)) return;
        setPending(true);
        setError(null);
        try {
            const response = await deleteTemplate(apiKey, template.id);
            setFeedback(response.message);
            setSelected(null);
            setVersions(null);
            setRevision((value) => value + 1);
        } catch (cause) {
            setError(cause instanceof ApiError ? cause.message : "The template could not be deleted.");
        } finally {
            setPending(false);
        }
    }

    return (
        <>
            <PageTitle eyebrow="CONTENT / LIBRARY" title="Templates" description="Manage tenant-owned notification templates and inspect their immutable version history." actions={<button className="button button-primary" onClick={startCreate}>Create template</button>} />
            {error ? <ErrorNotice message={error} /> : null}
            {feedback ? <div className="notice notice-success" role="status">{feedback}</div> : null}
            <section className="panel">
                <div className="filter-bar">
                    <div className="filter-field"><label htmlFor="template-channel">Channel filter</label><select id="template-channel" value={channel} onChange={(event) => { setChannel(event.target.value); setPage(0); }}><option value="">All channels</option>{channels.map((value) => <option key={value}>{value}</option>)}</select></div>
                    <p className="filter-note">Filter and pagination are applied by the management API.</p>
                </div>
                {loading ? <LoadingState label="Loading templates" /> : null}
                {!loading && items.length === 0 ? <EmptyState title="No templates found" description="Create a template to make it available for asynchronous notification submission." action={<button className="button button-primary" onClick={startCreate}>Create template</button>} /> : null}
                {items.length > 0 ? <div className="table-wrap"><table className="data-table">
                    <thead><tr><th>Template</th><th>Key</th><th>Channel</th><th>Tags</th><th>Created</th><th>Actions</th></tr></thead>
                    <tbody>{items.map((template) => <tr key={template.id}>
                        <td className="primary-cell">{template.name}</td><td className="mono">{template.templateKey}</td><td>{template.channel}</td><td>{template.tags?.join(", ") || "—"}</td><td>{formatDate(template.createdAt)}</td>
                        <td><div className="row-actions"><button className="button button-secondary button-small" disabled={pending} onClick={() => startEdit(template)}>Edit</button><button className="button button-secondary button-small" onClick={() => void inspectVersions(template)}>Versions</button><button className="button button-danger button-small" disabled={pending} onClick={() => void removeTemplate(template)}>Delete</button></div></td>
                    </tr>)}</tbody>
                </table></div> : null}
                <div className="pagination"><span>{totalPages} page{totalPages === 1 ? "" : "s"} · 50 records per request</span><div className="pagination-actions"><button className="button button-secondary button-small" disabled={page === 0 || loading} onClick={() => setPage((value) => Math.max(0, value - 1))}>Previous</button><span className="page-number">{totalPages ? page + 1 : 0}</span><button className="button button-secondary button-small" disabled={last || loading} onClick={() => setPage((value) => value + 1)}>Next</button></div></div>
            </section>

            {(creating || editing) ? <section className="panel template-editor">
                <div className="panel-heading"><div><h2>{creating ? "Create template" : `Edit ${selected?.name ?? "template"}`}</h2><p>{creating ? "A unique template key identifies this template to the send endpoint." : "Saving updates template metadata and creates a new active version."}</p></div><button className="button button-secondary button-small" onClick={() => { setCreating(false); setEditing(false); }}>Close</button></div>
                <form className="inline-form" onSubmit={saveTemplate}>
                    {fieldErrors.required ? <p className="field-error">{fieldErrors.required}</p> : null}
                    <div className="form-grid">
                        {creating ? <div className="form-field"><label htmlFor="template-key">Template key *</label><input id="template-key" maxLength={100} value={fields.templateKey} onChange={(event) => setFields({ ...fields, templateKey: event.target.value })} required /><span className="form-help">Maximum 100 characters. This key is used by notification submission.</span>{fieldErrors.templateKey ? <span className="field-error">{fieldErrors.templateKey}</span> : null}</div> : null}
                        <div className="form-field"><label htmlFor="template-name">Name *</label><input id="template-name" maxLength={100} value={fields.name} onChange={(event) => setFields({ ...fields, name: event.target.value })} required />{fieldErrors.name ? <span className="field-error">{fieldErrors.name}</span> : null}</div>
                        <div className="form-field"><label htmlFor="template-channel-select">Channel *</label><select id="template-channel-select" value={fields.channel} onChange={(event) => setFields({ ...fields, channel: event.target.value as Channel })}>{channels.map((value) => <option key={value}>{value}</option>)}</select></div>
                        <div className="form-field full"><label htmlFor="template-subject">Subject *</label><input id="template-subject" maxLength={255} value={fields.subject} onChange={(event) => setFields({ ...fields, subject: event.target.value })} required />{fieldErrors.subject ? <span className="field-error">{fieldErrors.subject}</span> : null}</div>
                        <div className="form-field full"><label htmlFor="template-body">Body *</label><textarea id="template-body" value={fields.body} onChange={(event) => setFields({ ...fields, body: event.target.value })} required />{fieldErrors.body ? <span className="field-error">{fieldErrors.body}</span> : null}</div>
                        <div className="form-field full"><label htmlFor="template-tags">Tags</label><input id="template-tags" value={fields.tags} onChange={(event) => setFields({ ...fields, tags: event.target.value })} placeholder="onboarding, transactional" /><span className="form-help">Separate tags with commas.</span></div>
                    </div>
                    <div className="form-footer"><button className="button button-primary" type="submit" disabled={pending}>{pending ? "Saving..." : creating ? "Create template" : "Save and create version"}</button></div>
                </form>
            </section> : null}

            {selected && versions ? <section className="panel versions-panel">
                <div className="panel-heading"><div><h2>Version history · {selected.name}</h2><p>{versions.length} version{versions.length === 1 ? "" : "s"} returned for this template</p></div><button className="button button-secondary button-small" onClick={() => setVersions(null)}>Close history</button></div>
                {versions.length === 0 ? <EmptyState title="No versions returned" description="The API returned no version history for this template." /> : <div className="version-list">{versions.map((version) => <details className="version-detail" key={version.id}>
                    <summary><strong>Version {version.versionNumber ?? version.version ?? "—"}</strong>{version.active ? <span className="version-active">Active</span> : null}<span>{formatDate(version.createdAt)}</span></summary>
                    <div className="version-content"><strong>{version.subject}</strong><pre>{version.body}</pre><span>Variables: {version.variables?.join(", ") || "None detected"}</span></div>
                </details>)}</div>}
            </section> : null}
        </>
    );
}