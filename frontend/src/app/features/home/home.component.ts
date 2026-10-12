import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-home',
  imports: [RouterLink],
  template: `
    <section class="hero">
      <img class="hero-photo" src="https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=2400&q=88" alt="Porsche 911, voiture de grand tourisme" fetchpriority="high">
      <div class="hero-wash"></div>
      <div class="hero-content">
        <p class="eyebrow">TUNISIA, AT YOUR OWN PACE</p>
        <h1>Luxury Mobility<br><em>in Tunisia</em></h1>
        <p class="hero-copy">Discover Tunisia with exceptional vehicles and<br class="desktop-break"> personalized chauffeur services.</p>
        <div class="hero-actions"><a class="button button-light" routerLink="/vehicles">Explore our fleet <span aria-hidden="true">↗</span></a><a class="text-link" routerLink="/contact">Request a quote <span aria-hidden="true">↗</span></a></div>
      </div>
      <div class="hero-caption"><span>01 — MOBILITY, CONSIDERED</span><span>49° 51′ N &nbsp; / &nbsp; 10° 10′ E</span></div>
    </section>
    <section class="intro-section">
      <div class="intro-index">01 <span>/</span> THE EXPERIENCE</div>
      <div class="intro-copy"><p class="eyebrow">A MORE PERSONAL WAY TO TRAVEL</p><h2>Made for the<br><em>long way round.</em></h2><p>From the first mile to the final destination, discover a considered collection of vehicles and personal mobility services for the Tunisian coast, city and countryside.</p></div>
      <div class="intro-mark" aria-hidden="true">ET</div>
    </section>
    <section class="services-section">
      <div class="section-heading"><div><p class="eyebrow">HOW WE MOVE</p><h2>Choose your <em>way.</em></h2></div><a class="text-link dark-link" routerLink="/services">Discover our services <span aria-hidden="true">↗</span></a></div>
      <div class="service-grid">
        <a class="service-item" routerLink="/vehicles"><span class="service-number">01</span><div><h3>Self-drive</h3><p>Time and space to make the journey your own.</p></div><span class="service-arrow" aria-hidden="true">↗</span></a>
        <a class="service-item" routerLink="/chauffeur"><span class="service-number">02</span><div><h3>Private chauffeur</h3><p>A discreet, personal service shaped around your day.</p></div><span class="service-arrow" aria-hidden="true">↗</span></a>
        <a class="service-item" routerLink="/airport-transfer"><span class="service-number">03</span><div><h3>Airport transfer</h3><p>A seamless arrival, from runway to destination.</p></div><span class="service-arrow" aria-hidden="true">↗</span></a>
      </div>
    </section>
    <section class="closing-section"><p class="eyebrow">YOUR NEXT CHAPTER STARTS HERE</p><h2>Go a little<br><em>further.</em></h2><a class="button button-dark" routerLink="/contact">Speak with our team <span aria-hidden="true">↗</span></a></section>
  `,
  styles: `
    :host { display:block; }
    .hero { position:relative; display:flex; min-height:min(700px,calc(100vh - 86px)); align-items:center; overflow:hidden; background:#1d2925; color:#fff; }
    .hero-photo, .hero-wash { position:absolute; inset:0; width:100%; height:100%; }
    .hero-photo { object-fit:cover; object-position:center 58%; animation:hero-arrive 1.2s ease-out both; }
    .hero-wash { background:linear-gradient(90deg,rgba(15,24,21,.82) 0%,rgba(15,24,21,.49) 48%,rgba(15,24,21,.08) 100%),linear-gradient(0deg,rgba(15,24,21,.42),transparent 24%); }
    .hero-content { position:relative; z-index:1; width:min(100%,1200px); margin:0 auto; padding:5rem clamp(1.5rem,8vw,8rem); animation:copy-arrive .8s .1s ease-out both; }
    .eyebrow { margin:0; color:#d1bc8d; font-size:.7rem; font-weight:700; letter-spacing:.16em; }
    h1, h2, h3, p { margin-top:0; }
    h1 { margin:1.3rem 0 1.2rem; font:500 clamp(3.7rem,8.2vw,7.2rem)/.98 var(--font-display); }
    h1 em, h2 em { color:#d1bc8d; font-weight:500; }
    .hero-copy { color:rgba(255,255,255,.82); font-size:1rem; line-height:1.8; }
    .hero-actions { display:flex; align-items:center; gap:2rem; margin-top:2rem; }
    .button { display:inline-flex; align-items:center; justify-content:center; gap:2rem; min-height:50px; padding:0 1.15rem; font-size:.8rem; font-weight:600; text-decoration:none; transition:background .2s,color .2s,transform .2s; }
    .button:hover { transform:translateY(-2px); }
    .button-light { background:#f6f5f1; color:#202720; }
    .text-link { color:#fff; font-size:.82rem; text-decoration:none; }
    .text-link span, .button span { color:#b5975d; }
    .hero-caption { position:absolute; z-index:1; right:clamp(1.5rem,8vw,8rem); bottom:1.7rem; left:clamp(1.5rem,8vw,8rem); display:flex; justify-content:space-between; color:rgba(255,255,255,.7); font-size:.62rem; letter-spacing:.12em; }
    .intro-section { position:relative; display:grid; max-width:1200px; min-height:460px; grid-template-columns:1fr 2fr 1fr; align-items:center; margin:0 auto; padding:6rem clamp(1.5rem,7vw,7rem); }
    .intro-index { align-self:start; color:var(--color-muted); font-size:.68rem; letter-spacing:.1em; }
    .intro-index span { margin:0 .5rem; color:var(--color-metal); }
    .intro-copy { max-width:560px; }
    .intro-copy .eyebrow, .section-heading .eyebrow, .closing-section .eyebrow { color:var(--color-olive); }
    h2 { margin:1rem 0; font:500 clamp(2.7rem,5.5vw,4.8rem)/1.03 var(--font-display); }
    .intro-copy h2 em, .section-heading h2 em, .closing-section h2 em { color:var(--color-olive); }
    .intro-copy > p:last-child { max-width:49ch; color:var(--color-muted); font-size:.9rem; line-height:1.9; }
    .intro-mark { justify-self:end; color:rgba(181,151,93,.24); font:500 5rem var(--font-display); }
    .services-section { padding:5.5rem clamp(1.5rem,7vw,7rem); background:#e9ece5; }
    .section-heading { display:flex; max-width:1060px; align-items:end; justify-content:space-between; margin:0 auto 2.5rem; }
    .section-heading h2 { margin-bottom:0; }
    .dark-link { color:var(--color-ink); }
    .service-grid { display:grid; max-width:1060px; grid-template-columns:repeat(3,minmax(0,1fr)); margin:0 auto; border-top:1px solid #c9cec5; }
    .service-item { display:grid; min-height:205px; grid-template-columns:30px 1fr 18px; align-items:start; gap:1rem; padding:1.6rem 1.2rem 1.6rem 0; border-bottom:1px solid #c9cec5; color:inherit; text-decoration:none; transition:background .2s; }
    .service-item + .service-item { padding-left:1.2rem; border-left:1px solid #c9cec5; }
    .service-item:hover { background:rgba(255,255,255,.34); }
    .service-number { color:var(--color-metal); font-size:.68rem; }
    .service-item h3 { margin:.2rem 0 .7rem; font:500 1.4rem var(--font-display); }
    .service-item p { max-width:25ch; margin:0; color:var(--color-muted); font-size:.8rem; line-height:1.7; }
    .service-arrow { color:var(--color-olive); }
    .closing-section { display:flex; min-height:400px; flex-direction:column; align-items:center; justify-content:center; padding:5rem 1.5rem; text-align:center; }
    .closing-section h2 { margin:1rem 0 1.6rem; }
    .button-dark { background:var(--color-ink); color:#fff; }
    .button-dark:hover { background:var(--color-olive); }
    @keyframes hero-arrive { from { transform:scale(1.035); opacity:.72; } to { transform:scale(1); opacity:1; } }
    @keyframes copy-arrive { from { transform:translateY(16px); opacity:0; } to { transform:translateY(0); opacity:1; } }
    @media(max-width:760px) {
      .hero { min-height:600px; min-height:calc(86svh - 72px); }
      .hero-photo { object-position:58% center; }
      .hero-wash { background:linear-gradient(90deg,rgba(15,24,21,.72),rgba(15,24,21,.2)),linear-gradient(0deg,rgba(15,24,21,.45),transparent 45%); }
      .hero-content { padding:4rem 1.5rem 6rem; }
      h1 { font-size:clamp(3.2rem,15vw,5.5rem); }
      .hero-copy { max-width:31ch; }
      .desktop-break { display:none; }
      .hero-actions { align-items:flex-start; flex-direction:column; gap:1.2rem; }
      .hero-caption { right:1.5rem; left:1.5rem; font-size:.55rem; }
      .hero-caption span:last-child { display:none; }
      .intro-section { min-height:0; grid-template-columns:1fr; gap:2rem; padding-top:4.5rem; padding-bottom:4.5rem; }
      .intro-mark { display:none; }
      .services-section { padding-top:4rem; padding-bottom:4rem; }
      .section-heading { align-items:flex-start; flex-direction:column; gap:1rem; }
      .service-grid { grid-template-columns:1fr; }
      .service-item, .service-item + .service-item { min-height:138px; padding:1.25rem 0; border-left:0; }
      .closing-section { min-height:350px; }
    }
    @media(prefers-reduced-motion:reduce) { *,*::before,*::after { animation-duration:.01ms!important; animation-iteration-count:1!important; scroll-behavior:auto!important; } }
  `,
})
export class HomeComponent {}