CREATE TABLE public."user"(
    id serial4,
    login VARCHAR(50) NOT NULL UNIQUE,
	"password" VARCHAR (30) NOT NULL,
	nickname VARCHAR (50) NOT NULL UNIQUE,
    CONSTRAINT user_pk PRIMARY KEY (id)
);

CREATE TABLE public."role"(
    id serial4,
    name VARCHAR(50) NOT NULL UNIQUE,
    CONSTRAINT role_pk PRIMARY KEY (id)
);

CREATE TABLE public.user_role(
    user_id INT,
    role_id INT,
    FOREIGN KEY (user_id) REFERENCES "user" (id),
    FOREIGN KEY (role_id) REFERENCES "role" (id),
    CONSTRAINT user_role_pk PRIMARY KEY (user_id, role_id)
);

INSERT INTO public."user" (login, "password", nickname)
VALUES ('admin', 'admin', 'admin');

INSERT INTO public."role" (name)
VALUES ('admin');

INSERT INTO public.user_role (user_id, role_id)
VALUES (currval('user_id_seq'), currval('role_id_seq'));

INSERT INTO public."role" (name)
VALUES ('user');
