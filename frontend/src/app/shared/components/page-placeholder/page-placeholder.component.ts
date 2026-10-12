import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-page-placeholder',
  template: `
    <section class="placeholder-page" aria-labelledby="page-title">
      <p class="eyebrow">ELITE TUNISIA <span> / </span> EN PRÉPARATION</p>
      <h1 id="page-title">{{ title }}</h1>
      <p class="placeholder-copy">{{ description }}</p>
      <div class="state-panel" role="status">
        <span class="state-mark" aria-hidden="true">i</span>
        <p>Cette partie n’est pas encore connectée à l’API métier.</p>
      </div>
    </section>
  `,
  styles: `
    .placeholder-page { max-width: 850px; min-height: 56vh; margin: 0 auto; padding: clamp(4rem, 12vw, 9rem) 1.5rem; }
    .eyebrow { color: var(--color-olive); font-size: .72rem; font-weight: 700; letter-spacing: .12em; }
    .eyebrow span { margin: 0 .5rem; color: var(--color-metal); }
    h1 { max-width: 15ch; margin: 1rem 0; font: 600 clamp(2.7rem, 7vw, 5rem)/1.02 var(--font-display); }
    .placeholder-copy { max-width: 52ch; color: var(--color-muted); font-size: 1.05rem; line-height: 1.75; }
    .state-panel { display: flex; align-items: center; gap: .9rem; margin-top: 3rem; padding: 1rem 1.2rem; border: 1px solid var(--color-line); background: var(--color-paper); }
    .state-panel p { margin: 0; color: var(--color-muted); font-size: .88rem; }
    .state-mark { display: grid; width: 1.6rem; aspect-ratio: 1; place-items: center; border-radius: 50%; background: var(--color-sage); color: var(--color-ink); font-weight: 700; }
  `,
})
export class PagePlaceholderComponent {
  private readonly route = inject(ActivatedRoute);
  protected readonly title = this.route.snapshot.data['title'] as string;
  protected readonly description = this.route.snapshot.data['description'] as string;
}