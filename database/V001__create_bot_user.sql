create table if not exists bot_user (
    qq_id bigint not null,
    provider varchar(32) not null default 'DIVING_FISH',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    primary key (qq_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
