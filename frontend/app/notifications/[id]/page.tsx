"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { RefreshCw, RotateCcw } from "lucide-react";
import { useApiKey } from "@/components/api-key-context";
import { BackLink, ErrorNotice, LoadingState, PageTitle, StatusPill } from "@/components/ui";
import { ApiError, getNotification, retryNotification } from "@/lib/api";
import type { NotificationSummary } from "@/types/api";

function formatDate(value: string | null) {
    if (!value) return "Not recorded";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "Unavailable" : date.toLocaleString();
}

export default function NotificationDetailPage() {
    const { id } = useParams<{ id: string }>();
    const { apiKey } = useApiKey();
    const [item, setItem] = useState<NotificationSummary | null>(null);
    const [loading, setLoading] = useState(true);
    const [pending, setPending] = useState(false);
    const [revision, setRevision] = useState(0);
    const [error, setError] = useState<string | null>(null);
    const [feedback, setFeedback] = useState<string | null>(null);

    useEffect(() => {
        if (!apiKey || !id) return;
        let active = true;
        setLoading(true);
        setError(null);
        getNotification(apiKey, id).then((response) => {
            if (active) setItem(response);
        }).catch((cause: unknown) => {
            if (active) setError(cause instanceof ApiError ? cause.message : "Notification details could not be loaded.");
        }).finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [apiKey, id, revision]);

    async function retry() {
        if (!apiKey || !item || !window.confirm("Queue this failed notification for another delivery attempt? The request does not guarantee delivery.")) return;
        setPending(true);
        setError(null);
        setFeedback(null);
        try {
            const response = await retryNotification(apiKey, item.id);
            setFeedback(`${response.message}. Delivery is not yet confirmed.`);
            setRevision((value) => value + 1);
        } catch (cause) {
            setError(cause instanceof ApiError ? cause.message : "The retry request could not be completed.");
        } finally {
            setPending(false);
        }
    }

    const canRetry = item?.retryEligible === true;

    return (
        <>
            <BackLink href="/notifications">All notifications</BackLink>
            <PageTitle
                eyebrow="DELIVERY / RECORD"
                title="Notification detail"
                description={id}
                actions={<>
                    <button className="button button-secondary" disabled={loading} onClick={() => setRevision((value) => value + 1)}><RefreshCw size={15} className={loading ? "spin" : ""} />Refresh status</button>
                    {canRetry ? <button className="button button-primary" disabled={pending} onClick={retry}><RotateCcw size={15} />{pending ? "Queueing retry..." : "Retry notification"}</button> : null}
                </>}
            />
            {error ? <ErrorNotice message={error} retry={() => setRevision((value) => value + 1)} /> : null}
            {feedback ? <div className="notice notice-success" role="status">{feedback}</div> : null}
            {loading && !item ? <LoadingState label="Loading notification details" /> : null}
            {item ? <>
                <section className="panel detail-panel">
                    <div className="panel-heading"><div><h2>Delivery state</h2><p>Current response from the notification management API</p></div><StatusPill status={item.status} /></div>
                    <div className="detail-grid">
                        <div className="detail-item"><span className="detail-label">Notification ID</span><span className="detail-value mono">{item.id}</span></div>
                        <div className="detail-item"><span className="detail-label">Recipient</span><span className="detail-value">{item.recipient}</span></div>
                        <div className="detail-item"><span className="detail-label">Channel</span><span className="detail-value">{item.channel}</span></div>
                        <div className="detail-item"><span className="detail-label">Template</span><span className="detail-value">{item.templateKey}</span></div>
                        <div className="detail-item"><span className="detail-label">Template reference</span><span className="detail-value mono">{item.templateVersionId || item.templateId || "Not recorded"}</span></div>
                        <div className="detail-item"><span className="detail-label">Provider</span><span className="detail-value">{item.providerName || "Not recorded"}</span></div>
                        <div className="detail-item"><span className="detail-label">Retry count</span><span className="detail-value">{item.retryCount}</span></div>
                        <div className="detail-item"><span className="detail-label">Created</span><span className="detail-value">{formatDate(item.createdAt)}</span></div>
                        <div className="detail-item"><span className="detail-label">Last updated</span><span className="detail-value">{formatDate(item.updatedAt)}</span></div>
                        <div className="detail-item"><span className="detail-label">Provider-confirmed delivery</span><span className="detail-value">{formatDate(item.sentAt)}</span></div>
                    </div>
                </section>
                {item.status === "FAILED" && !canRetry ? <div className="notice notice-error retry-note">The backend reports that this notification is not eligible for retry.</div> : null}
                <p className="detail-disclaimer">Message content and raw provider failure details are intentionally not shown in this dashboard.</p>
            </> : null}
        </>
    );
}