"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ArrowUpRight, RefreshCw } from "lucide-react";
import { useApiKey } from "@/components/api-key-context";
import { CountPanel, ErrorNotice, LoadingState, PageTitle, StatusPill } from "@/components/ui";
import { ApiError, getGatewayHealth, getNotificationCount, getNotifications } from "@/lib/api";
import type { GatewayHealth, NotificationStatus, NotificationSummary, PageResponse } from "@/types/api";

interface OverviewData {
    total: number;
    counts: Record<NotificationStatus, number>;
    recent: PageResponse<NotificationSummary>;
    health: GatewayHealth | null;
}

function dateLabel(value: string) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "Unavailable" : date.toLocaleString();
}

export default function OverviewPage() {
    const { apiKey } = useApiKey();
    const [revision, setRevision] = useState(0);
    const [data, setData] = useState<OverviewData | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!apiKey) return;
        let active = true;
        setLoading(true);
        setData(null);
        setError(null);

        Promise.all([
            getNotifications(apiKey, { page: 0, size: 5 }),
            getNotificationCount(apiKey, "QUEUED"),
            getNotificationCount(apiKey, "PROCESSING"),
            getNotificationCount(apiKey, "SENT"),
            getNotificationCount(apiKey, "FAILED"),
            getGatewayHealth().catch(() => null),
        ]).then(([recent, queued, processing, sent, failed, health]) => {
            if (!active) return;
            setData({
                total: recent.totalElements,
                counts: { QUEUED: queued, PROCESSING: processing, SENT: sent, FAILED: failed },
                recent,
                health,
            });
        }).catch((cause: unknown) => {
            if (!active) return;
            setError(cause instanceof ApiError ? cause.message : "Overview data could not be loaded.");
        }).finally(() => {
            if (active) setLoading(false);
        });

        return () => { active = false; };
    }, [apiKey, revision]);

    return (
        <>
            <PageTitle
                eyebrow="OPERATIONS / OVERVIEW"
                title="Notification overview"
                description="A live view of notification throughput and gateway availability for your tenant."
                actions={<button className="button button-secondary" onClick={() => setRevision((value) => value + 1)} disabled={loading}><RefreshCw size={15} className={loading ? "spin" : ""} />Refresh</button>}
            />

            {error ? <ErrorNotice message={error} retry={() => setRevision((value) => value + 1)} /> : null}
            {loading && !data ? <LoadingState label="Retrieving platform totals" /> : null}

            <div className="count-grid" aria-live="polite">
                <CountPanel label="All notifications" value={data?.total ?? null} note="Across every delivery status" />
                <CountPanel label="Queued" value={data?.counts.QUEUED ?? null} status="QUEUED" note="Waiting for a worker" />
                <CountPanel label="Processing" value={data?.counts.PROCESSING ?? null} status="PROCESSING" note="Currently in delivery" />
                <CountPanel label="Delivered" value={data?.counts.SENT ?? null} status="SENT" note="Confirmed by the provider" />
                <CountPanel label="Failed" value={data?.counts.FAILED ?? null} status="FAILED" note="Requires review" />
            </div>

            <div className="overview-lower">
                <section className="panel">
                    <div className="panel-heading">
                        <div><h2>Recent notifications</h2><p>Latest records returned by the management API</p></div>
                        <Link href="/notifications" className="table-link">View all <ArrowUpRight size={14} /></Link>
                    </div>
                    {loading && !data ? <LoadingState label="Loading recent activity" /> : null}
                    {!loading && data?.recent.content.length === 0 ? <div className="activity-placeholder"><strong>Nothing has moved yet</strong><p>New notifications will appear here once your tenant starts sending through NotifyFlow.</p><Link className="button button-primary" href="/send">Create a notification</Link></div> : null}
                    {data?.recent.content.length ? (
                        <div className="table-wrap">
                            <table className="data-table">
                                <thead><tr><th>Recipient</th><th>Template</th><th>Status</th><th>Created</th></tr></thead>
                                <tbody>{data.recent.content.map((item) => (
                                    <tr key={item.id}>
                                        <td><Link className="table-link" href={`/notifications/${encodeURIComponent(item.id)}`}>{item.recipient}</Link></td>
                                        <td>{item.templateKey}</td>
                                        <td><StatusPill status={item.status} /></td>
                                        <td>{dateLabel(item.createdAt)}</td>
                                    </tr>
                                ))}</tbody>
                            </table>
                        </div>
                    ) : null}
                </section>

                <section className="panel">
                    <div className="panel-heading"><div><h2>Gateway availability</h2><p>Public gateway health endpoint only</p></div>{data?.health ? <StatusPill status={data.health.status} /> : null}</div>
                    <div className="panel-body">
                        {data?.health ? <>
                            <div className="health-line"><span className="connection-dot" /><span>API Gateway</span><StatusPill status={data.health.status} /></div>
                            <div className="health-line"><span>Management service</span><span className="muted-cell">Not exposed here</span></div>
                            <div className="health-line"><span>Provider health</span><span className="muted-cell">Not exposed here</span></div>
                            <Link className="table-link" href="/health">Open system health <ArrowUpRight size={14} /></Link>
                        </> : loading ? <LoadingState label="Checking gateway" /> : <p className="muted-cell">Gateway health is unavailable.</p>}
                    </div>
                </section>
            </div>
        </>
    );
}