set @reporter_display_name_exists = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 'arcade_occupancy_report'
      and column_name = 'reporter_display_name'
);

set @add_reporter_display_name_sql = if(
    @reporter_display_name_exists = 0,
    'alter table arcade_occupancy_report add column reporter_display_name varchar(128) not null default '''' after reporter_user_id',
    'select 1'
);

prepare add_reporter_display_name_statement
    from @add_reporter_display_name_sql;
execute add_reporter_display_name_statement;
deallocate prepare add_reporter_display_name_statement;
