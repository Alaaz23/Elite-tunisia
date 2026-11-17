// Le pool "forks" ne démarre pas de façon fiable sur les postes Windows de référence
// (Timeout waiting for worker) ; "threads" exécute les mêmes tests sans ce blocage.
export default {
  test: {
    pool: 'threads',
  },
};
