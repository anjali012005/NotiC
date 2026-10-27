"use client";

import { createContext, useContext, useState } from "react";
import { ApiError, getProviders } from "@/lib/api";

interface ApiKeyContextValue {
    apiKey: string | null;
    connecting: boolean;
    error: string | null;
    connect: (apiKey: string) => Promise<void>;
    disconnect: () => void;
}

const ApiKeyContext = createContext<ApiKeyContextValue | null>(null);

export function ApiKeyProvider({ children }: { children: React.ReactNode }) {
    const [apiKey, setApiKey] = useState<string | null>(null);
    const [connecting, setConnecting] = useState(false);
    const [error, setError] = useState<string | null>(null);

    async function connect(candidate: string) {
        setConnecting(true);
        setError(null);
        try {
            await getProviders(candidate);
            setApiKey(candidate);
        } catch (cause) {
            setError(cause instanceof ApiError ? cause.message : "The API key could not be verified.");
        } finally {
            setConnecting(false);
        }
    }

    function disconnect() {
        setApiKey(null);
        setError(null);
    }

    return (
        <ApiKeyContext.Provider value={{ apiKey, connecting, error, connect, disconnect }}>
            {children}
        </ApiKeyContext.Provider>
    );
}

export function useApiKey() {
    const context = useContext(ApiKeyContext);
    if (!context) throw new Error("useApiKey must be used inside ApiKeyProvider");
    return context;
}