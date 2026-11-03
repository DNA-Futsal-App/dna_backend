-- Preserva a aprovação exata do vídeo no momento em que o Top 3 foi escolhido.
-- O backfill só é feito quando a aprovação é anterior à publicação.
-- Registros legados inconsistentes permanecem NULL e são bloqueados pela API.
ALTER TABLE award_pre_vote_finalists
    ADD COLUMN approved_at TIMESTAMPTZ;

UPDATE award_pre_vote_finalists f
SET approved_at = e.reviewed_at
FROM award_registration_entries e
WHERE e.id = f.entry_id
  AND e.review_status = 'APPROVED'
  AND e.reviewed_at IS NOT NULL
  AND e.reviewed_at <= f.finalized_at;
