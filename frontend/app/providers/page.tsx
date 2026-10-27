"use client";

import { useEffect, useState, type FormEvent } from "react";
import { Plus, RefreshCw, ShieldCheck } from "lucide-react";
import { useApiKey } from "@/components/api-key-context";
import { EmptyState, ErrorNotice, LoadingState, PageTitle, StatusPill } from "@/components/ui";
import { ApiError, createProvider, getProviders, setProviderDefault, setProviderEnabled } from "@/lib/api";
import type { Channel, Provider } from "@/types/api";

const channels: Channel[] = ["EMAIL", "SMS", "PUSH", "WHATSAPP"];

export default function ProvidersPage() {
    const { apiKey } = useApiKey();
    const [items, setItems] = useState<Provider[]>([]);
    const [channel, setChannel] = useState("");
    const [enabled, setEnabled] = useState("");
    const [revision, setRevision] = useState(0);
    const [pendingId, setPendingId] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [feedback, setFeedback] = useState<string | null>(null);
    const [showCreate, setShowCreate] = useState(false);
    const [creating, setCreating] = useState(false);
    const [name, setName] = useState("");
    const [fromEmail, setFromEmail] = useState("");
    const [fromName, setFromName] = useState("");

    useEffect(() => {
        if (!apiKey) return;
        let active = true;
        setLoading(true);
        setItems([]);
        setError(null);
        getProviders(apiKey, {
            channel: channel || undefined,
            enabled: enabled === "" ? undefined : enabled === "true",
        }).then((response) => {
            if (active) setItems(response);
        }).catch((cause: unknown) => {
            if (active) setError(cause instanceof ApiError ? cause.message : "Provider configurations could not be loaded.");
        }).finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [apiKey, channel, enabled, revision]);

    async function toggle(provider: Provider) {
        if (!apiKey) return;
        const nextState = !provider.enabled;
        if (!nextState && !window.confirm(`Disable ${provider.name}? Notifications using this provider may fail.`)) return;
        setPendingId(provider.id);
        setError(null);
        setFeedback(null);
        try {
            const updated = await setProviderEnabled(apiKey, provider.id, nextState);
            setFeedback(`${updated.name} is now ${updated.enabled ? "enabled" : "disabled"}.`);
            setRevision((value) => value + 1);
        } catch (cause) {
            setError(cause instanceof ApiError ? cause.message : "The provider setting could not be changed.");
        } finally {
            setPendingId(null);
        }
    }

    async function makeDefault(provider: Provider) {
        if (!apiKey || provider.isDefault) return;
        setPendingId(provider.id);
        setError(null);
        setFeedback(null);
        try {
            const updated = await setProviderDefault(apiKey, provider.id);
            setFeedback(`${updated.name} is now the default provider.`);
            setRevision((value) => value + 1);
        } catch (cause) {
            setError(cause instanceof ApiError ? cause.message : "The default provider could not be changed.");
        } finally {
            setPendingId(null);
        }
    }

    async function submitProvider(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!apiKey) return;
        setCreating(true);
        setError(null);
        setFeedback(null);
        try {
            const config = JSON.stringify({
                fromEmail: fromEmail.trim(),
                ...(fromName.trim() ? { fromName: fromName.trim() } : {}),
            });
            const created = await createProvider(apiKey, {
                name: name.trim(),
                channel: "EMAIL",
                providerType: "SENDGRID",
                config,
                enabled: false,
                isDefault: false,
            });
            setShowCreate(false);
            setName("");
            setFromEmail("");
            setFromName("");
            setFeedback(created.credentialsConfigured
                ? `${created.name} was created${created.isDefault ? " and set as the EMAIL default" : ""}.`
                : `${created.name} was saved, but the backend SendGrid credential is missing. Set SENDGRID_API_KEY before sending.`);
            setRevision((value) => value + 1);
        } catch (cause) {
            setError(cause instanceof ApiError ? cause.message : "The provider could not be created.");
        } finally {
            setCreating(false);
        }
    }

    return (
        <>
            <PageTitle eyebrow="DELIVERY / PROVIDERS" title="Providers" description="Configure this tenant’s SendGrid email provider and manage its enabled/default state." actions={<><button className="button button-primary" onClick={() => { setShowCreate((value) => !value); setError(null); }}><Plus size={15} />Add provider</button><button className="button button-secondary" onClick={() => setRevision((value) => value + 1)} disabled={loading}><RefreshCw size={15} className={loading ? "spin" : ""} />Refresh</button></>} />
            {error ? <ErrorNotice message={error} /> : null}
            {feedback ? <div className="notice notice-success" role="status">{feedback}</div> : null}
            <div className="notice provider-safety-note"><ShieldCheck size={17} /><span>SendGrid API keys stay in the management service’s SENDGRID_API_KEY environment setting. This form only stores the verified sender email and display name; the secret is never entered or shown here.</span></div>
            {showCreate ? <section className="panel provider-form-panel">
                <div className="panel-heading"><div><h2>Add SendGrid email provider</h2><p>New providers start disabled. Once credentials are ready, enable it and set it as the EMAIL default.</p></div><button className="button button-secondary button-small" type="button" onClick={() => setShowCreate(false)} disabled={creating}>Cancel</button></div>
                <form className="panel-body" onSubmit={(event) => void submitProvider(event)}>
                    <div className="form-grid">
                        <div className="form-field"><label htmlFor="provider-name">Provider name</label><input id="provider-name" value={name} onChange={(event) => setName(event.target.value)} maxLength={100} required placeholder="Primary SendGrid" /></div>
                        <div className="form-field"><label htmlFor="provider-from-email">Verified sender email</label><input id="provider-from-email" type="email" value={fromEmail} onChange={(event) => setFromEmail(event.target.value)} required placeholder="notifications@example.com" /></div>
                        <div className="form-field"><label htmlFor="provider-from-name">Sender display name <span className="optional-label">Optional</span></label><input id="provider-from-name" value={fromName} onChange={(event) => setFromName(event.target.value)} maxLength={100} placeholder="NotifyFlow" /></div>
                        <div className="form-field provider-fixed-type"><label>Channel and provider</label><div>EMAIL · SendGrid</div></div>
                    </div>
                    <div className="form-footer"><button className="button button-secondary" type="button" onClick={() => setShowCreate(false)} disabled={creating}>Cancel</button><button className="button button-primary" type="submit" disabled={creating}>{creating ? "Saving…" : "Save provider"}</button></div>
                </form>
            </section> : null}
            <section className="panel">
                <div className="filter-bar">
                    <div className="filter-field"><label htmlFor="provider-channel">Channel</label><select id="provider-channel" value={channel} onChange={(event) => setChannel(event.target.value)}><option value="">All channels</option>{channels.map((value) => <option key={value}>{value}</option>)}</select></div>
                    <div className="filter-field"><label htmlFor="provider-enabled">Enabled</label><select id="provider-enabled" value={enabled} onChange={(event) => setEnabled(event.target.value)}><option value="">All providers</option><option value="true">Enabled</option><option value="false">Disabled</option></select></div>
                </div>
                {loading ? <LoadingState label="Loading provider configurations" /> : null}
                {!loading && items.length === 0 ? <EmptyState title="No providers found" description="No provider configurations match this tenant and the selected filters." action={<button className="button button-primary" onClick={() => setShowCreate(true)}><Plus size={15} />Add SendGrid provider</button>} /> : null}
                {items.length > 0 ? <div className="table-wrap"><table className="data-table">
                    <thead><tr><th>Name</th><th>Type</th><th>Channel</th><th>Sender config</th><th>Credential</th><th>Enabled</th><th>Default</th><th>Actions</th></tr></thead>
                    <tbody>{items.map((provider) => <tr key={provider.id}>
                        <td className="primary-cell">{provider.name}</td><td>{provider.providerType}</td><td>{provider.channel}</td>
                        <td><StatusPill status={provider.configured ? "CONFIGURED" : "NOT CONFIGURED"} /></td>
                        <td><StatusPill status={provider.credentialsConfigured ? "CREDENTIAL READY" : "CREDENTIAL MISSING"} /></td>
                        <td><StatusPill status={provider.enabled ? "ENABLED" : "DISABLED"} /></td>
                        <td>{provider.isDefault ? <span className="default-label">Default</span> : "—"}</td>
                        <td><div className="row-actions"><button className={`button ${provider.enabled ? "button-danger" : "button-secondary"} button-small`} disabled={pendingId === provider.id || (!provider.enabled && (!provider.configured || !provider.credentialsConfigured))} onClick={() => void toggle(provider)}>{pendingId === provider.id ? "Saving..." : provider.enabled ? "Disable" : !provider.configured ? "Set sender" : !provider.credentialsConfigured ? "Add credentials" : "Enable"}</button>{!provider.isDefault ? <button className="button button-secondary button-small" disabled={pendingId === provider.id || !provider.enabled || !provider.credentialsConfigured} onClick={() => void makeDefault(provider)}>Set default</button> : null}</div></td>
                    </tr>)}</tbody>
                </table></div> : null}
            </section>
        </>
    );
}
