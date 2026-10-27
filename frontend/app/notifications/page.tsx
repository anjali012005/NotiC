"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { RefreshCw } from "lucide-react";
import { useApiKey } from "@/components/api-key-context";
import { EmptyState, ErrorNotice, LoadingState, PageTitle, StatusPill } from "@/components/ui";
import { ApiError, getNotifications } from "@/lib/api";
import type { Channel, NotificationStatus, NotificationSummary, PageResponse } from "@/types/api";

const statusOptions: NotificationStatus[] = ["QUEUED", "PROCESSING", "SENT", "FAILED"];
const channelOptions: Channel[] = ["EMAIL", "SMS", "PUSH", "WHATSAPP"];

function formatDate(value: string) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "Unavailable" : date.toLocaleString();
}

export default function NotificationsPage() {
    const { apiKey } = useApiKey();
    const [page, setPage] = useState(0);
    const [revision, setRevision] = useState(0);
    const [status, setStatus] = useState("");
    const [channel, setChannel] = useState("");
    const [recipientInput, setRecipientInput] = useState("");
    const [recipient, setRecipient] = useState("");
    const [result, setResult] = useState<PageResponse<NotificationSummary> | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!apiKey) return;
        let active = true;
        setLoading(true);
        setResult(null);
        setError(null);
        getNotifications(apiKey, {
            page,
            size: 20,
            status: status as NotificationStatus | undefined,
            channel: channel || undefined,
            recipient: recipient || undefined,
        }).then((response) => {
            if (active) setResult(response);
        }).catch((cause: unknown) => {
            if (active) setError(cause instanceof ApiError ? cause.message : "Notifications could not be loaded.");
        }).finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [apiKey, page, status, channel, recipient, revision]);

    function applyFilters(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setPage(0);
        setRecipient(recipientInput.trim());
        setRevision((value) => value + 1);
    }

    const totalPages = result?.totalPages ?? 0;

    return (
        <>
            <PageTitle
                eyebrow="DELIVERY / RECORDS"
                title="Notifications"
                description="Search the tenant-scoped delivery records and inspect the status returned by the management service."
                actions={<button className="button button-secondary" onClick={() => setRevision((value) => value + 1)} disabled={loading}><RefreshCw size={15} className={loading ? "spin" : ""} />Refresh</button>}
            />
            {error ? <ErrorNotice message={error} retry={() => setRevision((value) => value + 1)} /> : null}
            <section className="panel">
                <form className="filter-bar" onSubmit={applyFilters}>
                    <div className="filter-field"><label htmlFor="status-filter">Status</label><select id="status-filter" value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}><option value="">All statuses</option>{statusOptions.map((value) => <option key={value}>{value}</option>)}</select></div>
                    <div className="filter-field"><label htmlFor="channel-filter">Channel</label><select id="channel-filter" value={channel} onChange={(event) => { setChannel(event.target.value); setPage(0); }}><option value="">All channels</option>{channelOptions.map((value) => <option key={value}>{value}</option>)}</select></div>
                    <div className="filter-field recipient-filter"><label htmlFor="recipient-filter">Recipient contains</label><input id="recipient-filter" type="text" value={recipientInput} onChange={(event) => setRecipientInput(event.target.value)} placeholder="Search email, number or address" /></div>
                    <button className="button button-primary button-small" type="submit">Apply filters</button>
                </form>
                {loading && !result ? <LoadingState label="Loading notification records" /> : null}
                {!loading && result?.content.length === 0 ? <EmptyState title="No matching notifications" description="There are no records for the current filters. Clear a filter or submit a new notification." /> : null}
                {result?.content.length ? (
                    <div className="table-wrap">
                        <table className="data-table">
                            <thead><tr><th>Notification</th><th>Recipient</th><th>Channel</th><th>Template</th><th>Status</th><th>Retries</th><th>Created</th></tr></thead>
                            <tbody>{result.content.map((item) => (
                                <tr key={item.id}>
                                    <td className="mono"><Link className="table-link" href={`/notifications/${encodeURIComponent(item.id)}`}>{item.id.slice(0, 8)}...</Link></td>
                                    <td>{item.recipient}</td><td>{item.channel}</td><td>{item.templateKey}</td>
                                    <td><StatusPill status={item.status} /></td><td>{item.retryCount}</td><td>{formatDate(item.createdAt)}</td>
                                </tr>
                            ))}</tbody>
                        </table>
                    </div>
                ) : null}
                {result ? <div className="pagination"><span>{result.totalElements.toLocaleString()} matching records · Page {totalPages ? page + 1 : 0} of {totalPages}</span><div className="pagination-actions"><button className="button button-secondary button-small" disabled={page <= 0 || loading} onClick={() => setPage((value) => Math.max(0, value - 1))}>Previous</button><button className="button button-secondary button-small" disabled={result.last || loading} onClick={() => setPage((value) => value + 1)}>Next</button></div></div> : null}
            </section>
        </>
    );
}