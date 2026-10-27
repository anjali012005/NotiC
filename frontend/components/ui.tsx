import type { ReactNode } from "react";
import { AlertCircle, ArrowLeft, LoaderCircle } from "lucide-react";
import Link from "next/link";
import type { NotificationStatus } from "@/types/api";

export function PageTitle({
    eyebrow,
    title,
    description,
    actions,
}: {
    eyebrow: string;
    title: string;
    description: string;
    actions?: ReactNode;
}) {
    return (
        <div className="page-heading">
            <div>
                <p className="eyebrow">{eyebrow}</p>
                <h1>{title}</h1>
                <p className="page-description">{description}</p>
            </div>
            {actions ? <div className="page-actions">{actions}</div> : null}
        </div>
    );
}

export function StatusPill({ status }: { status: string }) {
    const value = status.toUpperCase();
    const known = ["QUEUED", "PROCESSING", "SENT", "FAILED", "UP", "DOWN", "UNKNOWN", "HEALTHY", "UNHEALTHY", "CONFIGURED", "NOT CONFIGURED", "ENABLED", "DISABLED", "CREDENTIAL READY", "CREDENTIAL MISSING"];
    return <span className={`status-pill status-${value.toLowerCase()}`}>{known.includes(value) ? value : "UNKNOWN"}</span>;
}

export function LoadingState({ label = "Loading NotifyFlow data" }: { label?: string }) {
    return <div className="state-panel" role="status"><LoaderCircle className="spin" size={20} />{label}</div>;
}

export function ErrorNotice({ message, retry }: { message: string; retry?: () => void }) {
    return (
        <div className="notice notice-error" role="alert">
            <AlertCircle size={18} aria-hidden="true" />
            <span>{message}</span>
            {retry ? <button className="text-button" onClick={retry}>Try again</button> : null}
        </div>
    );
}

export function EmptyState({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
    return <div className="empty-state"><span className="empty-mark" aria-hidden="true">—</span><h2>{title}</h2><p>{description}</p>{action}</div>;
}

export function CountPanel({ label, value, status, note }: { label: string; value: number | null; status?: NotificationStatus; note: string }) {
    return (
        <section className={`count-panel count-${status?.toLowerCase() ?? "total"}`} aria-label={`${label} count`}>
            <div className="count-panel-top"><span>{label}</span>{status ? <StatusPill status={status} /> : null}</div>
            <strong>{value === null ? "--" : value.toLocaleString()}</strong>
            <span className="count-note">{note}</span>
        </section>
    );
}

export function BackLink({ href, children }: { href: string; children: ReactNode }) {
    return <Link className="back-link" href={href}><ArrowLeft size={16} aria-hidden="true" />{children}</Link>;
}
