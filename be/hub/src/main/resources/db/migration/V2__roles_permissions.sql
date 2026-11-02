insert into permissions (code, description) values
    ('USERS_MANAGE', 'Gestire gli utenti'),
    ('INTEGRATIONS_MANAGE', 'Gestire integrazioni e agenti'),
    ('SETTINGS_MANAGE', 'Gestire configurazioni, budget e limiti AI'),
    ('AI_USAGE_READ', 'Vedere il registro d''uso dell''AI (solo metadati)'),
    ('AI_USE', 'Utilizzare l''AI'),
    ('SYSTEM_READ', 'Vedere lo stato del sistema'),
    ('DOCS_READ', 'Consultare i documenti autorizzati'),
    ('PROTOCOL_READ', 'Leggere i protocolli'),
    ('PROTOCOL_WRITE', 'Gestire i protocolli'),
    ('PROTOCOL_CANCEL', 'Annullare protocolli'),
    ('CALENDAR_WRITE', 'Gestire il calendario'),
    ('REFERENCES_READ', 'Leggere le referenze'),
    ('REFERENCES_WRITE', 'Modificare e approvare le referenze'),
    ('TENDERS_WORK', 'Lavorare sulle gare assegnate'),
    ('TENDERS_ALL', 'Vedere tutte le gare individuate e assegnarle'),
    ('DL_MANAGE', 'Gestire le attivita di Direzione Lavori'),
    ('DL_APPROVE', 'Approvare i report DL'),
    ('BIM_ADMIN', 'Amministrare la modellazione BIM');

insert into roles (code, name) values
    ('ADMINISTRATOR', 'Amministratore'),
    ('COLLABORATOR', 'Collaboratore autorizzato'),
    ('DL_MANAGER', 'Responsabile DL'),
    ('TENDER_MANAGER', 'Responsabile gare'),
    ('BIM_ADMIN', 'Amministratore BIM');

-- Administrator: all administrative and operational areas except tenders (D-06) and others' chats (B.5).
insert into role_permissions (role_code, permission_code) values
    ('ADMINISTRATOR', 'USERS_MANAGE'), ('ADMINISTRATOR', 'INTEGRATIONS_MANAGE'),
    ('ADMINISTRATOR', 'SETTINGS_MANAGE'), ('ADMINISTRATOR', 'AI_USAGE_READ'),
    ('ADMINISTRATOR', 'AI_USE'), ('ADMINISTRATOR', 'SYSTEM_READ'), ('ADMINISTRATOR', 'DOCS_READ'),
    ('ADMINISTRATOR', 'PROTOCOL_READ'), ('ADMINISTRATOR', 'PROTOCOL_WRITE'),
    ('ADMINISTRATOR', 'PROTOCOL_CANCEL'), ('ADMINISTRATOR', 'CALENDAR_WRITE'),
    ('ADMINISTRATOR', 'REFERENCES_READ'), ('ADMINISTRATOR', 'REFERENCES_WRITE'),
    ('ADMINISTRATOR', 'DL_APPROVE'),
    ('COLLABORATOR', 'AI_USE'), ('COLLABORATOR', 'SYSTEM_READ'), ('COLLABORATOR', 'DOCS_READ'),
    ('COLLABORATOR', 'PROTOCOL_READ'), ('COLLABORATOR', 'PROTOCOL_WRITE'),
    ('COLLABORATOR', 'CALENDAR_WRITE'), ('COLLABORATOR', 'REFERENCES_READ'),
    ('COLLABORATOR', 'TENDERS_WORK'),
    ('DL_MANAGER', 'DL_MANAGE'), ('DL_MANAGER', 'DL_APPROVE'), ('DL_MANAGER', 'SYSTEM_READ'),
    ('TENDER_MANAGER', 'TENDERS_ALL'), ('TENDER_MANAGER', 'TENDERS_WORK'),
    ('BIM_ADMIN', 'BIM_ADMIN'), ('BIM_ADMIN', 'SYSTEM_READ');
