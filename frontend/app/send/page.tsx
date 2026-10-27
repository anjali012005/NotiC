"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { ArrowUpRight, Send } from "lucide-react";
import { useApiKey } from "@/components/api-key-context";
import { EmptyState, ErrorNotice, LoadingState, PageTitle, StatusPill } from "@/components/ui";
import { ApiError, getTemplateVariables, getTemplates, sendNotification } from "@/lib/api";
import type { NotificationAccepted, Template } from "@/types/api";

export default function SendNotificationPage() {
    const { apiKey } = useApiKey();
    const [templates, setTemplates] = useState<Template[]>([]);
    const [templateId, setTemplateId] = useState("");
    const [variables, setVariables] = useState<string[]>([]);
    const [values, setValues] = useState<Record<string, string>>({});
    const [recipient, setRecipient] = useState("");
    const [loadingTemplates, setLoadingTemplates] = useState(true);
    const [loadingVariables, setLoadingVariables] = useState(false);
    const [pending, setPending] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
    const [accepted, setAccepted] = useState<NotificationAccepted | null>(null);

    useEffect(() => {
        if (!apiKey) return;
        let active = true;
        getTemplates(apiKey, 0).then((response) => {
            if (!active) return;
            setTemplates(response.content);
            setTemplateId(response.content[0]?.id ?? "");
        }).catch((cause: unknown) => {
            if (active) setError(cause instanceof ApiError ? cause.message : "Templates could not be loaded.");
        }).finally(() => { if (active) setLoadingTemplates(false); });
        return () => { active = false; };
    }, [apiKey]);

    useEffect(() => {
        if (!apiKey || !templateId) {
            setVariables([]);
            return;
        }
        let active = true;
        setLoadingVariables(true);
        getTemplateVariables(apiKey, templateId).then((names) => {
            if (active) {
                setVariables(names);
                setValues(Object.fromEntries(names.map((name) => [name, ""])));
            }
        }).catch((cause: unknown) => {
            if (active) setError(cause instanceof ApiError ? cause.message : "Template variables could not be loaded.");
        }).finally(() => { if (active) setLoadingVariables(false); });
        return () => { active = false; };
    }, [apiKey, templateId]);

    const selectedTemplate = useMemo(() => templates.find((template) => template.id === templateId), [templates, templateId]);

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError(null);
        setFieldErrors({});
        setAccepted(null);
        if (!selectedTemplate) {
            setFieldErrors({ templateKey: "Choose a template." });
            return;
        }
        if (!recipient.trim()) {
            setFieldErrors({ recipient: "Recipient is required." });
            return;
        }
        if (!apiKey) return;
        setPending(true);
        try {
            const result = await sendNotification(apiKey, {
                templateKey: selectedTemplate.templateKey,
                recipient: recipient.trim(),
                variables: Object.fromEntries(Object.entries(values).map(([name, value]) => [name, value.trim()])),
            });
            setAccepted(result);
            setRecipient("");
            setValues(Object.fromEntries(variables.map((name) => [name, ""])));
        } catch (cause) {
            if (cause instanceof ApiError) {
                setError(cause.message);
                setFieldErrors(cause.fieldErrors);
            } else setError("The notification request could not be submitted.");
        } finally {
            setPending(false);
        }
    }

    const acceptedId = accepted?.id || accepted?.notificationId;

    return (
        <>
            <PageTitle eyebrow="DELIVERY / SUBMIT" title="Send notification" description="Submit a notification for asynchronous processing. Acceptance is not the same as confirmed delivery." />
            {error ? <ErrorNotice message={error} /> : null}
            {accepted ? <div className="notice notice-success accepted-notice" role="status"><div><strong>Request accepted for processing</strong><p>Current status: <StatusPill status={accepted.status} /> {accepted.message}</p>{acceptedId ? <Link className="table-link" href={`/notifications/${encodeURIComponent(acceptedId)}`}>Track notification <ArrowUpRight size={14} /></Link> : null}</div></div> : null}
            {loadingTemplates ? <LoadingState label="Loading available templates" /> : null}
            {!loadingTemplates && templates.length === 0 ? <section className="panel"><EmptyState title="No templates available" description="Create a notification template before submitting a message." action={<Link className="button button-primary" href="/templates">Open templates</Link>} /></section> : null}
            {!loadingTemplates && templates.length > 0 ? (
                <form className="panel inline-form" onSubmit={submit}>
                    <div className="form-grid">
                        <div className="form-field full"><label htmlFor="send-template">Template</label><select id="send-template" value={templateId} onChange={(event) => { setTemplateId(event.target.value); setAccepted(null); }} required><option value="">Choose a template</option>{templates.map((template) => <option key={template.id} value={template.id}>{template.name} · {template.channel} · {template.templateKey}</option>)}</select><span className="form-help">Options returned on the first template page.</span>{fieldErrors.templateKey ? <span className="field-error">{fieldErrors.templateKey}</span> : null}</div>
                        <div className="form-field full"><label htmlFor="send-recipient">Recipient</label><input id="send-recipient" value={recipient} onChange={(event) => setRecipient(event.target.value)} autoComplete="email" required placeholder="Address, phone number or destination" />{fieldErrors.recipient ? <span className="field-error">{fieldErrors.recipient}</span> : null}</div>
                        <div className="form-field full"><label>Template variables</label>{loadingVariables ? <span className="form-help">Loading variables...</span> : variables.length === 0 ? <span className="form-help">This template has no detected variables.</span> : <div className="form-grid">{variables.map((name) => <div className="form-field" key={name}><label htmlFor={`variable-${name}`}>{name}</label><input id={`variable-${name}`} value={values[name] ?? ""} onChange={(event) => setValues((current) => ({ ...current, [name]: event.target.value }))} /></div>)}</div>}</div>
                    </div>
                    <div className="form-footer"><button type="submit" className="button button-primary" disabled={pending || loadingVariables || !selectedTemplate}><Send size={15} />{pending ? "Submitting..." : "Submit notification"}</button></div>
                </form>
            ) : null}
        </>
    );
}