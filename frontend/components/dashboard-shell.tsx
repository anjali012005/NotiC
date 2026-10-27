"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Activity, BellRing, Boxes, ChartNoAxesCombined, CirclePlus, KeyRound, LayoutDashboard, LogOut, Radio, ShieldCheck } from "lucide-react";
import { ApiKeyProvider, useApiKey } from "@/components/api-key-context";

const navigation = [
    { href: "/", label: "Overview", icon: LayoutDashboard },
    { href: "/notifications", label: "Notifications", icon: BellRing },
    { href: "/send", label: "Send notification", icon: CirclePlus },
    { href: "/templates", label: "Templates", icon: Boxes },
    { href: "/providers", label: "Providers", icon: Radio },
    { href: "/health", label: "System health", icon: ChartNoAxesCombined },
];

function ApiKeyGate() {
    const { apiKey, connect, disconnect, connecting, error } = useApiKey();
    const [candidate, setCandidate] = useState("");
    const [formError, setFormError] = useState("");

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setFormError("");
        if (!candidate.trim()) {
            setFormError("Enter the API key configured for your tenant.");
            return;
        }
        await connect(candidate.trim());
        setCandidate("");
    }

    if (apiKey) return null;

    return (
        <div className="gate-backdrop">
            <section className="gate-panel" aria-labelledby="gate-title">
                <span className="gate-icon"><KeyRound size={20} /></span>
                <p className="eyebrow">SECURE CONNECTION</p>
                <h1 id="gate-title">Connect to NotifyFlow</h1>
                <p className="gate-copy">Use the tenant API key supplied by your administrator. It is held in memory for this session only.</p>
                <form className="stack-form" onSubmit={submit}>
                    <label htmlFor="api-key">Tenant API key</label>
                    <input id="api-key" type="password" autoComplete="off" value={candidate} onChange={(event) => setCandidate(event.target.value)} />
                    {formError || error ? <p className="field-error" role="alert">{formError || error}</p> : null}
                    <button className="button button-primary" disabled={connecting} type="submit">{connecting ? "Verifying key..." : "Connect securely"}</button>
                </form>
                <p className="gate-footnote"><ShieldCheck size={15} /> Tenant scope is resolved by the backend.</p>
            </section>
        </div>
    );
}

function ShellContent({ children }: { children: React.ReactNode }) {
    const pathname = usePathname();
    const { apiKey, disconnect } = useApiKey();

    return (
        <>
            <div className="app-shell">
                <aside className="sidebar">
                    <Link className="brand" href="/" aria-label="NotifyFlow overview">
                        <span className="brand-mark"><Activity size={21} strokeWidth={2.4} /></span>
                        <span>notify<span>flow</span><small>MESSAGING PLATFORM</small></span>
                    </Link>
                    <p className="nav-label">WORKSPACE</p>
                    <nav className="primary-nav" aria-label="Primary navigation">
                        {navigation.map(({ href, label, icon: Icon }) => {
                            const active = href === "/" ? pathname === "/" : pathname.startsWith(href);
                            return <Link key={href} href={href} className={`nav-link${active ? " active" : ""}`} aria-current={active ? "page" : undefined}><Icon size={18} strokeWidth={1.8} /><span>{label}</span>{href === "/notifications" ? <span className="nav-pip" /> : null}</Link>;
                        })}
                    </nav>
                    <div className="sidebar-bottom">
                        <div className="connection-card"><span className="connection-dot" /><span>{apiKey ? "API key connected" : "Not connected"}</span><ShieldCheck size={16} /></div>
                        <div className="sidebar-caption">NOTIFYFLOW <span>·</span> SPRINT 18</div>
                    </div>
                </aside>
                <div className="main-region">
                    <header className="topbar">
                        <div className="breadcrumb"><span>Workspace</span><span className="crumb-divider">/</span><strong>{navigation.find((item) => item.href === "/" ? pathname === "/" : pathname.startsWith(item.href))?.label ?? "Notification detail"}</strong></div>
                        <div className="topbar-right"><span className="tenant-indicator"><span className="connection-dot" />Tenant-scoped</span>{apiKey ? <button className="icon-button" type="button" aria-label="Disconnect API key" title="Disconnect API key" onClick={disconnect}><LogOut size={17} /></button> : null}</div>
                    </header>
                    <main className="content-area">{children}</main>
                </div>
            </div>
            <ApiKeyGate />
        </>
    );
}

export function DashboardShell({ children }: { children: React.ReactNode }) {
    return <ApiKeyProvider><ShellContent>{children}</ShellContent></ApiKeyProvider>;
}