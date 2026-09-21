create table if not exists arcade_occupancy_report (
    group_id bigint not null,
    venue varchar(32) not null,
    player_count int not null,
    reporter_user_id bigint not null,
    reported_at timestamp not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    primary key (group_id, venue),
    key idx_arcade_occupancy_reported_at (reported_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
