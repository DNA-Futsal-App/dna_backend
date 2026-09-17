# Prêmio DNA Futsal — homologação ponta a ponta

Este roteiro deve ser executado em ambiente local ou de homologação com dados descartáveis. Não use a edição oficial para votos de teste.

## Pré-requisitos

- backend, frontend e scraper em execução;
- conta ADMIN com token novo contendo `SCOPE_ADMIN`;
- edição de teste em `DRAFT`;
- pelo menos duas equipes importadas no mesmo contexto de divisão/categoria;
- atletas classificados em GOLEIRO, FIXO, ALA e PIVO;
- pelo menos dois técnicos ativos.

## 1. Snapshot administrativo

1. Abra `/app/admin/premio-dna`.
2. Importe as equipes do contexto de teste.
3. Confirme que reimportar o mesmo time não duplica candidatos.
4. Atribua posição a todos os atletas.
5. Confirme que `positionsPending` chega a zero.
6. Tente abrir a votação antes de classificar todos os atletas e confirme que o backend recusa.

Critério de aceite: somente snapshot completo pode sair de `DRAFT`.

## 2. Convite — conta nova

1. Gere um convite para um técnico.
2. Copie o link no momento da geração.
3. Abra o link em janela anônima.
4. Confirme treinador, divisão, categoria e equipe.
5. Clique em `Fazer cadastro`.
6. Crie uma conta descartável e confirme o e-mail.
7. Entre na conta.
8. Confirme que a aba `Votação` aparece.
9. Confirme no painel admin que o técnico passou para `REGISTERED`.

Critério de aceite: a conta criada pelo convite fica ligada exatamente ao técnico convidado.

## 3. Convite — conta existente

1. Gere convite para outro técnico.
2. Abra o link e escolha `Já tenho conta`.
3. Entre com uma conta existente ainda não vinculada à edição.
4. Confirme o redirecionamento para `/app/votacao-treinador`.
5. Confirme no painel admin o vínculo da conta.

Critério de aceite: nenhuma segunda conta é criada.

## 4. Segurança do convite

Verifique os cenários abaixo:

- reutilizar convite já reivindicado;
- usar convite revogado;
- usar convite expirado;
- tentar gerar novo convite para técnico já vinculado;
- tentar reivindicar convite reservado por outro cadastro;
- usuário comum chamar `/api/v1/admin/awards/**`.

Critério de aceite: todos são bloqueados pelo backend, independentemente da UI.

## 5. Cédula do treinador

Com a edição aberta:

1. Entre como treinador habilitado.
2. Para cada categoria, selecione um time e depois um candidato.
3. Na categoria Técnico, confirme que o próprio treinador não aparece.
4. Confirme a tela de revisão antes do envio.
5. Registre o voto.
6. Confirme o redirecionamento para a tela de agradecimento.
7. Volte manualmente para `/app/votacao-treinador`.

Critério de aceite: a cédula é registrada uma única vez e não pode ser editada.

## 6. Testes negativos da cédula

Usando cliente HTTP com um token de treinador de homologação, tente enviar:

- categoria obrigatória ausente;
- mesma categoria duas vezes;
- candidato de outra divisão/categoria;
- atleta de posição diferente;
- candidato inativo;
- voto no próprio técnico;
- segunda submissão da cédula.

Critério de aceite: nenhum caso inválido persiste voto.

## 7. Ranking parcial deve permanecer oculto

Com a edição em `OPEN`:

1. Abra `/app/admin/premio-dna/apuracao`.
2. Confirme que aparecem apenas contagens e integridade.
3. Chame `GET /api/v1/admin/awards/editions/{editionId}/results` diretamente.

Resultado esperado: HTTP 409 com `AWARD_RESULTS_LOCKED_UNTIL_CLOSE`.

Critério de aceite: nem ADMIN recebe ranking por candidato antes do encerramento.

## 8. Auditoria

Na tela de apuração:

- `ballotsSubmitted` deve bater com o total do painel operacional;
- `completeBallots` deve ser igual a `ballotsSubmitted`;
- `invalidBallots` deve ser zero;
- `integrityOk` deve ser `true`.

A auditoria valida:

- vínculo da cédula com treinador e usuário;
- presença de todas as categorias obrigatórias;
- uma escolha por categoria;
- candidato pertencente à edição e ao contexto do treinador;
- tipo ATHLETE/COACH correto;
- posição correta para atletas;
- proibição de auto-voto para técnico.

Critério de aceite: inconsistência bloqueia a apuração final.

## 9. Encerramento e apuração

1. Encerre a edição no painel administrativo.
2. Abra novamente `/app/admin/premio-dna/apuracao`.
3. Confirme o ranking separado por divisão/categoria.
4. Confirme cada categoria: Goleiro, Fixo, Ala, Pivô e Técnico.
5. Valide votos, percentuais e empates com uma amostra conhecida.
6. Exporte o CSV e confira os mesmos totais.

Critério de aceite: o somatório de votos de cada categoria/contexto corresponde ao total de votos válidos daquela categoria/contexto.

## 10. Privacidade e publicação

Nesta fase não existe endpoint público de resultados. Treinadores também não recebem agregados ou ranking.

Critério de aceite: resultados ficam disponíveis somente sob `/api/v1/admin/**` e na tela administrativa de apuração.

A publicação oficial deve ser uma etapa separada e explícita, nunca consequência automática de encerrar a votação.
