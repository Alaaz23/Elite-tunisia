import { expect, test as base } from '@playwright/test';

export { expect };

const LOCAL_HOSTS = new Set(['127.0.0.1', 'localhost']);

// Les polices Google et l'image d'accueil (Unsplash) sont des ressources tierces : leur lenteur ou leur blocage réseau
// retardait l'événement `load` de page.goto au-delà du délai du test (traces : requêtes fonts.gstatic.com jamais terminées).
// Seules les requêtes vers l'application locale sont autorisées ; le comportement de l'application n'est pas masqué.
export const test = base.extend<{ blockThirdParty: void }>({
  blockThirdParty: [
    async ({ context }, use) => {
      await context.route(
        (url) => url.protocol.startsWith('http') && !LOCAL_HOSTS.has(url.hostname),
        (route) => route.abort('blockedbyclient'),
      );
      await use();
    },
    { auto: true },
  ],
});
