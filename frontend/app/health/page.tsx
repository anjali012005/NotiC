"use client";

import { useEffect, useState } from "react";
import { RefreshCw, ShieldCheck } from "lucide-react";
import { ErrorNotice, LoadingState, PageTitle, StatusPill } from "@/components/ui";
import { ApiError, getGatewayHealth } from "@/lib/api";
import type { GatewayHealth } from "@/types/api";

export default function HealthPage() {
    const [health, setHealth] = useState<GatewayHealth | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [revision, setRevision] = useState(0);

    useEffect(() => {
        let active = true;
        setLoading(true);
        setError(null);
        getGatewayHealth().then((response) => {
            if (active) setHealth(response);
        }).catch((cause: unknown) => {
            if (active) setError(cause instanceof ApiError ? cause.message : "Gateway health could not be retrieved.");
        }).finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [revision]);

    return (
        <>
            <PageTitle eyebrow="PLATFORM / HEALTH" title="System health" description="Only health information exposed safely through the API Gateway is shown. No management Actuator endpoints are called from the browser." actions={<button className="button button-secondary" onClick={() => setRevision((value) => value + 1)} disabled={loading}><RefreshCw size={15} className={loading ? "spin" : ""} />Refresh</button>} />
            {error ? <ErrorNotice message={error} retry={() => setRevision((value) => value + 1)} /> : null}
            <section className="panel">
                <div className="panel-heading"><div><h2>API Gateway</h2><p>GET /actuator/health · public summary</p></div>{health ? <StatusPill status={health.status} /> : null}</div>
                {loading ? <LoadingState label="Checking gateway health" /> : null}
                {health ? <div className="health-body">
                    <div className="health-line"><ShieldCheck size={17} /><span>Gateway status</span><StatusPill status={health.status} /></div>
                    {health.components && Object.entries(health.components).map(([name, component]) => <div className="health-line" key={name}><span>{name}</span><StatusPill status={component.status} /></div>)}
                    <p className="health-limitation">Management-service readiness, database and broker readiness, provider health, Prometheus metrics, queue/outbox depth and latency are not exposed through the gateway’s public routes.</p>
                </div> : !loading && !error ? <p className="health-unavailable">Gateway health is unavailable.</p> : null}
            </section>
        </>
    );
}