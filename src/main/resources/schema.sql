drop table customers if exists
;
create table customers
(
    id bigserial primary key,
    name varchar(255) not null
)
;
