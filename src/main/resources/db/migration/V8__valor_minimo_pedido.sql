alter table respostas_cotacao add column valor_minimo_pedido numeric(15,2);
alter table respostas_cotacao add column incluida_compra_sugerida boolean not null default true;

alter table respostas_cotacao
  add constraint ck_resposta_valor_minimo_pedido
  check (valor_minimo_pedido is null or valor_minimo_pedido > 0);

alter table pedidos_compra add column valor_minimo_pedido numeric(15,2);
alter table pedidos_compra add column abaixo_minimo_confirmado boolean not null default false;
