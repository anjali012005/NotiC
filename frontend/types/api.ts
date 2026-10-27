export type Channel = "EMAIL" | "SMS" | "PUSH" | "WHATSAPP";
export type NotificationStatus = "QUEUED" | "PROCESSING" | "SENT" | "FAILED";
export type ProviderType = "SMTP" | "SENDGRID" | "AWS_SES" | "TWILIO" | "MSG91" | "FIREBASE" | "ONESIGNAL";

export interface PageResponse<T> {
    content: T[];
    page: number;
    size: number;
    totalPages: number;
    totalElements: number;
    last: boolean;
}

export interface NotificationSummary {
    id: string;
    templateId: string;
    templateVersionId: string;
    templateKey: string;
    recipient: string;
    channel: Channel;
    providerId: string;
    providerName: string;
    status: NotificationStatus;
    retryCount: number;
    retryEligible: boolean;
    failureReason: string | null;
    createdAt: string;
    updatedAt: string;
    sentAt: string | null;
}

export interface NotificationAccepted {
    id: string;
    notificationId?: string;
    status: NotificationStatus;
    message: string;
}

export interface Template {
    id: string;
    templateKey: string;
    name: string;
    channel: Channel;
    subject: string;
    body: string;
    tags: string[] | null;
    createdAt: string;
    message?: string;
}

export interface TemplateVersion {
    id: string;
    version: number | null;
    versionNumber: number | null;
    name: string;
    channel: Channel;
    subject: string;
    body: string;
    tags: string[] | null;
    active: boolean;
    createdAt: string;
    updatedAt: string;
    variables: string[];
}

export interface Provider {
    id: string;
    name: string;
    channel: Channel;
    providerType: ProviderType;
    configured: boolean;
    credentialsConfigured: boolean;
    enabled: boolean;
    isDefault: boolean;
    createdAt: string;
    updatedAt: string;
    message?: string;
}

export interface GatewayHealth {
    status: string;
    components?: Record<string, { status: string }>;
}
