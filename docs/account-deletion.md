# Exclusão de conta

## Aplicativo

1. Exibir que a conta será bloqueada imediatamente, todas as inscrições e mídias serão removidas em 10 dias e os votos serão preservados.
2. Solicitar a senha atual e enviar `POST /api/v1/auth/account-deletion` com Bearer token e `{"currentPassword":"..."}`.
3. Após `202`, apresentar `scheduledFor` no fuso do usuário e limpar access/refresh tokens. O e-mail informa a mesma data no fuso configurado do servidor.
4. Ao tentar login com credenciais válidas antes do prazo, a API retorna `403 / ACCOUNT_PENDING_DELETION`, sem cancelar nem emitir sessão. Pedir confirmação de cancelamento antes de continuar para a página inicial. Somente se o usuário confirmar, enviar `POST /api/v1/auth/account-deletion/cancel` com `{"login":"email ou telefone","currentPassword":"..."}`, sem o JWT revogado.
5. Após `204`, efetuar novo login com as credenciais mantidas apenas em memória durante a confirmação e entrar na página inicial. Se o usuário recusar, voltar à tela de login e manter a exclusão. Login isolado, refresh, recuperação de senha e confirmação de e-mail não cancelam a exclusão. Não exibir opção separada de cancelamento na tela de login.

O prazo é exatamente 240 horas a partir da solicitação. O cancelamento deve ocorrer antes de `scheduledFor`; no instante do vencimento retorna `409`. Credenciais inválidas retornam `401`, payload inválido `400`, e mais de sete tentativas sem sucesso em 15 minutos retornam `429`. O limite é compartilhado por conta entre solicitação e cancelamento, em todas as instâncias. Uma conta inexistente usa hash de senha fictício.

Uma repetição concorrente válida não muda o prazo nem duplica o e-mail. O JWT revogado não permite repetir a solicitação: nesse caso, retorna `401`.

## Persistência e processamento

A migração V16 adiciona os estados `PENDING_DELETION`, `DELETING` e `DELETED`, datas e filas persistidas. Aplicar antes de habilitar o novo cliente e atualizar todas as instâncias: versões antigas mantêm caches de segurança incompatíveis com a revogação imediata. Não executar versões antigas em paralelo após habilitar a funcionalidade.

O worker executa a cada minuto (`app.account-deletion.delay`, milissegundos), em lotes de 20. Transações por conta e bloqueios PostgreSQL coordenam workers e cancelamentos. Indisponibilidades podem atrasar a execução, mas nunca antecipá-la. Durante o prazo, inscrições permanecem intactas e o processamento de vídeos fica suspenso. Cancelamento reativa a conta, mas não restaura tokens antigos; o recuperador de vídeos retoma os trabalhos pendentes.

No vencimento, são removidas inscrições de todas as edições, categorias inscritas, CPF, notificações, preferências, tokens e e-mails da outbox destinados ao endereço atual. O registro da conta perde dados pessoais e credenciais em `DELETING`; e-mail e telefone ficam disponíveis para um novo cadastro nesse momento. `DELETED` só é registrado após a limpeza externa. O novo cadastro nunca recupera os vínculos anteriores.

Votos e cédulas permanecem inalterados. Registros de eleitor necessários à apuração ficam inativos, sem credenciais ou convites, preservando identificadores e contexto esportivo. O registro técnico da conta também preserva referências administrativas. Isso não representa anonimização completa dos vínculos esportivos. Inscrições de terceiros avaliadas pelo usuário não são excluídas.

Objetos e autorizações da Oracle são registrados em `account_deletion_cleanup`. O registro de objetos durante uploads ocorre em transação independente antes do envio, permitindo recuperar arquivos mesmo após rollback. A coluna `user_id` dessa fila não tem FK: isso evita bloquear a transação independente enquanto a transação de upload mantém o bloqueio da conta. As contas são mantidas como registros técnicos, nunca apagadas fisicamente. Recursos de contas ativas ou pendentes não são processados pela fila.

As autorizações de upload são revogadas antes de remover objetos. Resposta 404 é sucesso; outras falhas têm retentativas crescentes até uma hora, sem abandono automático. Workers de vídeo seguram o bloqueio da conta durante o processamento para impedir gravações concorrentes com a exclusão. Essa escolha pode fazer a solicitação aguardar uma transcodificação em andamento e consome uma conexão durante o trabalho; dimensionar o pool para os workers e suas transações independentes.

Links externos são removidos do banco, mas arquivos de terceiros não são apagados. Backups, e-mails já entregues e dados históricos em provedores externos dependem das respectivas políticas de retenção. Mensagens antigas destinadas a endereços anteriores à troca de e-mail não possuem vínculo de proprietário no esquema legado e não podem ser atribuídas com segurança por este fluxo.

## Operação

Monitorar `account.deletion.overdue`, `account.deletion.cleanup.pending`, `account.deletion.failures`, `account.deletion.cleanup.failures`, `account.deletion.completed` e `mail.outbox.delivery.failures` (nomes exportados conforme o backend de métricas). Configurar alertas para exclusões atrasadas e falhas de e-mail; após dez falhas de limpeza também há log de erro, sem dados pessoais. O envio usa a política de retentativas da outbox; falha de entrega não altera o prazo. Tarefas em falha devem ser reprocessadas, não removidas manualmente para forçar conclusão.

Validação: `./mvnw test`. Os testes PostgreSQL usam Testcontainers e requerem Docker; sem Docker são ignorados. A integração cobre migração, transações, remoção de inscrições, preservação de votos, reuso de identificadores e retentativas de armazenamento. Testes de serviço/web cobrem senha, prazo, cancelamento e autenticação da rota.
