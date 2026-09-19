create table if not exists maimai_account_binding (
    qq_id bigint not null,
    arcade_credential_encrypted text not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    primary key (qq_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
