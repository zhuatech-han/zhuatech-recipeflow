-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

create table ingredient (
 id bigint auto_increment primary key,
 code varchar(30) not null,
 name varchar(120) not null,
 department_id bigint not null,
 category varchar(60) not null,
 unit varchar(10) not null,
 purchase_quantity decimal(18,3) not null,
 purchase_price decimal(18,4) not null,
 yield_percent decimal(8,2) not null,
 enabled boolean not null,
 version bigint not null
);

create table recipe (
 id bigint auto_increment primary key,
 code varchar(30) not null,
 name varchar(120) not null,
 department_id bigint not null,
 created_by bigint not null,
 reviewer_id bigint not null,
 portions int not null,
 instructions varchar(2000) not null,
 state varchar(20) not null,
 version bigint not null,
 active_revision_id bigint
);

create table recipe_line (
 id bigint auto_increment primary key,
 recipe_id bigint not null,
 ingredient_id bigint,
 component_revision_id bigint,
 quantity decimal(18,3) not null,
 unit varchar(10) not null
);

create table recipe_editor (
 id bigint auto_increment primary key,
 recipe_id bigint not null,
 account_id bigint not null
);

create table recipe_revision (
 id bigint auto_increment primary key,
 recipe_id bigint not null,
 number int not null,
 snapshot_json longtext not null,
 digest varchar(64) not null,
 decision varchar(20) not null,
 submitted_by bigint not null,
 reviewed_by bigint,
 created_at timestamp(6) not null
);

create table prep_batch (
 id bigint auto_increment primary key,
 code varchar(30) not null,
 name varchar(120) not null,
 department_id bigint not null,
 created_by bigint not null,
 operator_id bigint not null,
 service_date date not null,
 state varchar(20) not null,
 version bigint not null,
 snapshot_json longtext not null,
 digest varchar(64) not null,
 result_json longtext
);

create table consumption (
 id bigint auto_increment primary key,
 batch_id bigint not null,
 ingredient_id bigint not null,
 quantity decimal(18,3) not null,
 waste decimal(18,3) not null,
 note varchar(400) not null
);

create table prep_output (
 id bigint auto_increment primary key,
 batch_id bigint not null,
 revision_id bigint not null,
 portions int not null,
 note varchar(400) not null
);

create table business_event (
 id bigint auto_increment primary key,
 kind varchar(20) not null,
 object_id bigint not null,
 actor_id bigint not null,
 action varchar(30) not null,
 note varchar(400) not null,
 created_at timestamp(6) not null
);

alter table ingredient add unique(department_id,code);
alter table ingredient add foreign key(department_id) references department(id);
alter table recipe add unique(department_id,code);
alter table recipe add foreign key(department_id) references department(id);
alter table recipe add foreign key(created_by) references account(id);
alter table recipe add foreign key(reviewer_id) references account(id);
alter table recipe_line add foreign key(recipe_id) references recipe(id);
alter table recipe_line add foreign key(ingredient_id) references ingredient(id);
alter table recipe_line add foreign key(component_revision_id) references recipe_revision(id);
alter table recipe_line add check ((ingredient_id is null) <> (component_revision_id is null));
alter table recipe_editor add unique(recipe_id,account_id);
alter table recipe_editor add foreign key(recipe_id) references recipe(id);
alter table recipe_editor add foreign key(account_id) references account(id);
alter table recipe_revision add unique(recipe_id,number);
alter table recipe_revision add foreign key(recipe_id) references recipe(id);
alter table recipe_revision add foreign key(submitted_by) references account(id);
alter table recipe_revision add foreign key(reviewed_by) references account(id);
alter table recipe add foreign key(active_revision_id) references recipe_revision(id);
alter table prep_batch add unique(department_id,code);
alter table prep_batch add foreign key(department_id) references department(id);
alter table prep_batch add foreign key(created_by) references account(id);
alter table prep_batch add foreign key(operator_id) references account(id);
alter table consumption add unique(batch_id,ingredient_id);
alter table consumption add foreign key(batch_id) references prep_batch(id);
alter table consumption add foreign key(ingredient_id) references ingredient(id);
alter table prep_output add unique(batch_id,revision_id);
alter table prep_output add foreign key(batch_id) references prep_batch(id);
alter table prep_output add foreign key(revision_id) references recipe_revision(id);
alter table business_event add foreign key(actor_id) references account(id);
create index idx_recipe_scope on recipe(department_id,state);
create index idx_batch_scope on prep_batch(department_id,operator_id,state);
create index idx_event_object on business_event(kind,object_id,id);
