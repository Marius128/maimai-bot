create table if not exists lxns_oauth_binding (
    qq_id bigint not null,
    subject varchar(255) not null,
    username varchar(255) null,
    access_token_encrypted text not null,
    refresh_token_encrypted text not null,
    access_token_expires_at timestamp not null,
    refresh_token_expires_at timestamp not null,
    scope varchar(500) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    primary key (qq_id),
    unique key uk_lxns_oauth_subject (subject)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
