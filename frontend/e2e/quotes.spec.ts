import { APIRequestContext, Page } from '@playwright/test';
import { expect, test } from './fixtures';
import { Temporal } from '@js-temporal/polyfill';
import { ADMIN_EMAIL, ADMIN_PASSWORD } from './admin-credentials';
test.describe.serial('Quotes with PostgreSQL API',()=>{
  const suffix=Date.now().toString();const password='Quote-Strong-123!';const ownerEmail=`quote-owner-${suffix}@example.test`;const otherEmail=`quote-other-${suffix}@example.test`;const agentEmail=`quote-agent-${suffix}@example.test`;
  const start=Temporal.Now.zonedDateTimeISO('Africa/Tunis').add({days:60}).with({hour:10,minute:0,second:0,millisecond:0,microsecond:0,nanosecond:0});const end=start.add({hours:25});const valid=Temporal.Now.zonedDateTimeISO('Africa/Tunis').add({days:10}).with({hour:16,minute:0,second:0,millisecond:0,microsecond:0,nanosecond:0});
  let vehicleId='';let ownerToken='';let otherToken='';let adminToken='';let bookingId='';let quoteId='';let quoteReference='';
  test.beforeAll(async({request})=>{
    adminToken=await apiLogin(request,ADMIN_EMAIL,ADMIN_PASSWORD);
    const brand=await mutate(request,adminToken,'POST','/api/admin/brands',{name:`Quote Brand ${suffix}`,description:'E2E brand',active:true});const category=await mutate(request,adminToken,'POST','/api/admin/categories',{name:`Quote Category ${suffix}`,description:'E2E category',active:true});
    const vehicle=await mutate(request,adminToken,'POST','/api/admin/vehicles',{brandId:brand.id,categoryId:category.id,model:`Quote Touring ${suffix}`,year:2025,description:'E2E proposal vehicle',pricePerDay:500,pricePerWeek:3000,priceWithDriver:700,currency:'TND',passengers:5,doors:4,transmission:'AUTOMATIC',fuel:'PETROL',withDriverAvailable:true,status:'AVAILABLE',features:[],images:[]});vehicleId=vehicle.id as string;
    await mutate(request,adminToken,'POST','/api/admin/users',{firstName:'Quote',lastName:'Agent',email:agentEmail,phone:'+216 20 123 456',password,role:'AGENT',jobTitle:'Agent'});
    for(const email of [ownerEmail,otherEmail]){const token=await csrf(request);const response=await request.post('/api/auth/register',{headers:{'X-XSRF-TOKEN':token},data:{firstName:'Quote',lastName:'Client',email,phone:'+216 20 123 456',password,confirmPassword:password}});expect(response.status()).toBe(201);}
    ownerToken=await apiLogin(request,ownerEmail,password);otherToken=await apiLogin(request,otherEmail,password);
  });
  function input(driver=false){return {validUntil:valid.toInstant().toString(),withDriver:driver,discountAmount:0,customerNote:null,internalNote:null,services:[]};}
  async function demand(request:APIRequestContext,offset:number){return mutate(request,ownerToken,'POST','/api/bookings',{vehicleId,startDateTime:start.add({days:offset}).toInstant().toString(),endDateTime:end.add({days:offset}).toInstant().toString(),pickupLocation:'Tunis',dropoffLocation:'Tunis'});}
  async function sent(request:APIRequestContext,offset:number){const booking=await demand(request,offset);const quote=await mutate(request,adminToken,'POST',`/api/admin/quotes/booking/${booking.id}`,input());await mutate(request,adminToken,'POST',`/api/admin/quotes/${quote.id}/send`,{});return {booking,quote};}
  test('customer demand then agent draft, chauffeur line, server total and send',async({page})=>{
    await login(page,ownerEmail,password);await page.goto(`/vehicles/${vehicleId}`);
    await page.getByLabel('Début de location').fill(start.toPlainDateTime().toString({smallestUnit:'minute'}));await page.getByLabel('Fin de location').fill(end.toPlainDateTime().toString({smallestUnit:'minute'}));await page.getByLabel('Lieu de prise en charge').fill('Tunis');await page.getByLabel('Lieu de retour').fill('Tunis');await page.getByRole('button',{name:'Vérifier disponibilité'}).click();await page.getByRole('button',{name:'Envoyer ma demande'}).click();await expect(page).toHaveURL(/\/customer\/bookings\/[a-f0-9-]+$/);bookingId=page.url().split('/').at(-1)!;
    await page.getByRole('button',{name:'Déconnexion',exact:true}).click();await login(page,agentEmail,password,true);await page.goto(`/admin/bookings/${bookingId}`);await page.getByRole('link',{name:'Créer un devis',exact:true}).click();
    await page.getByLabel('Validité du devis').fill(valid.toPlainDateTime().toString({smallestUnit:'minute'}));await page.getByLabel('Inclure le chauffeur').check();await page.getByLabel('Remise agence').fill('50');
    const creation=page.waitForResponse(response=>response.url().includes('/api/admin/quotes/booking/')&&response.request().method()==='POST').then(response=>response.json());await page.getByRole('button',{name:'Enregistrer le brouillon'}).click();const created=await creation;quoteId=created.id as string;quoteReference=created.reference as string;expect(created.totalAmount).toBe(1350);expect(created.lines.map((line:{type:string})=>line.type)).toContain('DRIVER');
    await expect(page.getByRole('heading',{name:quoteReference,exact:true})).toBeVisible();await page.getByRole('button',{name:'Envoyer le devis'}).click();await expect(page.locator('header .badge')).toHaveText('Envoyé');
    await page.getByRole('button',{name:'Déconnexion',exact:true}).click();await login(page,ownerEmail,password);await page.goto('/customer/quotes');await page.getByRole('link',{name:quoteReference,exact:true}).click();await expect(page.locator('dd.total')).toContainText('350');
  });
  test('customer accepts; booking stays PENDING until agency confirms',async({page,request})=>{
    await login(page,ownerEmail,password);await page.goto(`/customer/quotes/${quoteId}`);await page.getByRole('button',{name:'Accepter le devis'}).click();await expect(page.locator('header .badge')).toHaveText('Accepté');await expect(page.getByRole('button',{name:'Accepter le devis'})).toHaveCount(0);
    const pending=await request.get(`/api/bookings/${bookingId}`,{headers:{Authorization:`Bearer ${ownerToken}`}});expect((await pending.json()).status).toBe('PENDING');
    await page.getByRole('button',{name:'Déconnexion',exact:true}).click();await login(page,agentEmail,password,true);await page.goto(`/admin/quotes/${quoteId}`);await expect(page.locator('header .badge')).toHaveText('Accepté');await page.getByRole('button',{name:'Valider la réservation'}).click();await expect(page.getByText('Réservation confirmée.',{exact:true})).toBeVisible();
    const confirmed=await request.get(`/api/bookings/${bookingId}`,{headers:{Authorization:`Bearer ${ownerToken}`}});const result=await confirmed.json();expect(result.status).toBe('CONFIRMED');expect(result.totalAmount).toBe(1350);
  });
  test('customer rejection is visible to agency',async({page,request})=>{
    const fixture=await sent(request,5);await login(page,ownerEmail,password);await page.goto(`/customer/quotes/${fixture.quote.id}`);await page.getByRole('button',{name:'Refuser le devis'}).click();await expect(page.locator('header .badge')).toHaveText('Refusé');
    const result=await request.get(`/api/admin/quotes/${fixture.quote.id}`,{headers:{Authorization:`Bearer ${adminToken}`}});expect((await result.json()).status).toBe('REJECTED');
  });
  test('change request creates new version and preserves previous snapshot',async({page,request})=>{
    const fixture=await sent(request,10);await login(page,ownerEmail,password);await page.goto(`/customer/quotes/${fixture.quote.id}`);await page.getByLabel('Motif ou demande (facultatif)').fill('Ajouter le chauffeur.');await page.getByRole('button',{name:'Demander une modification'}).click();await expect(page.getByRole('status')).toContainText('demande de modification');
    await page.getByRole('button',{name:'Déconnexion',exact:true}).click();await login(page,agentEmail,password,true);await page.goto(`/admin/quotes/${fixture.quote.id}`);await expect(page.getByText('Ajouter le chauffeur.',{exact:true})).toBeVisible();await page.getByRole('link',{name:'Nouvelle version',exact:true}).click();await page.getByLabel('Validité du devis').fill(valid.toPlainDateTime().toString({smallestUnit:'minute'}));await page.getByLabel('Inclure le chauffeur').check();await page.getByRole('button',{name:'Enregistrer le brouillon'}).click();await expect(page.getByText('PROPOSITION COMMERCIALE · VERSION 2',{exact:true})).toBeVisible();await page.getByRole('button',{name:'Envoyer le devis'}).click();await expect(page.locator('header .badge')).toHaveText('Envoyé');
    const old=await request.get(`/api/admin/quotes/${fixture.quote.id}`,{headers:{Authorization:`Bearer ${adminToken}`}});const preserved=await old.json();expect(preserved.version).toBe(1);expect(preserved.totalAmount).toBe(1000);expect(preserved.status).toBe('CANCELLED');
  });
  test('foreign customer cannot read or accept a quote',async({page,request})=>{
    const denied=await request.get(`/api/customer/quotes/${quoteId}`,{headers:{Authorization:`Bearer ${otherToken}`}});expect(denied.status()).toBe(404);const token=await csrf(request);const accept=await request.post(`/api/customer/quotes/${quoteId}/accept`,{headers:{Authorization:`Bearer ${otherToken}`,'X-XSRF-TOKEN':token},data:{}});expect(accept.status()).toBe(404);await login(page,otherEmail,password);await page.goto(`/customer/quotes/${quoteId}`);await expect(page.getByRole('alert')).toBeVisible();await expect(page.getByRole('heading',{name:quoteReference,exact:true})).toHaveCount(0);
  });
  test('accepted proposal cannot confirm after another booking takes the vehicle',async({page,request})=>{
    const fixture=await sent(request,15);await mutate(request,ownerToken,'POST',`/api/customer/quotes/${fixture.quote.id}/accept`,{});
    const rival=await mutate(request,otherToken,'POST','/api/bookings',{vehicleId,startDateTime:start.add({days:15}).toInstant().toString(),endDateTime:end.add({days:15}).toInstant().toString(),pickupLocation:'Tunis',dropoffLocation:'Tunis'});await mutate(request,adminToken,'PATCH',`/api/admin/bookings/${rival.id}/confirm`,{});
    await login(page,agentEmail,password,true);await page.goto(`/admin/quotes/${fixture.quote.id}`);await page.getByRole('button',{name:'Valider la réservation'}).click();await expect(page.getByRole('alert')).toContainText('pas disponible');const pending=await request.get(`/api/bookings/${fixture.booking.id}`,{headers:{Authorization:`Bearer ${ownerToken}`}});expect((await pending.json()).status).toBe('PENDING');
  });
  test('customer pays the server snapshot once without confirming the booking', async ({ page, request }) => {
    const fixture = await sent(request, 20);
    await mutate(request, ownerToken, 'POST', `/api/customer/quotes/${fixture.quote.id}/accept`, {});
    await login(page, ownerEmail, password);
    await page.goto(`/customer/quotes/${fixture.quote.id}`);
    const endpoint = `/api/customer/quotes/${fixture.quote.id}/payments`;
    const paymentResponse = page.waitForResponse(response => response.url().endsWith(endpoint) && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Payer', exact: true }).click();
    const response = await paymentResponse;
    expect(response.status()).toBe(200);
    const payment = await response.json();
    expect(payment.status).toBe('CAPTURED');
    expect(payment.amount).toBe(fixture.quote.totalAmount);
    expect(payment.transactionRef).toMatch(/^SIM-/);
    await expect(page.getByText('Paiement réussi.', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Payer', exact: true })).toBeDisabled();
    const repeated = await mutate(request, ownerToken, 'POST', endpoint, response.request().postDataJSON());
    expect(repeated.id).toBe(payment.id);
    expect(repeated.transactionRef).toBe(payment.transactionRef);
    const booking = await request.get(`/api/bookings/${fixture.booking.id}`, { headers: { Authorization: `Bearer ${ownerToken}` } });
    expect(booking.status()).toBe(200);
    expect((await booking.json()).status).toBe('PENDING');
  });
});
async function csrf(request:APIRequestContext):Promise<string>{return (await (await request.get('/api/auth/csrf')).json()).token as string;}
async function apiLogin(request:APIRequestContext,email:string,password:string):Promise<string>{const token=await csrf(request);const response=await request.post('/api/auth/login',{headers:{'X-XSRF-TOKEN':token},data:{email,password}});expect(response.status()).toBe(200);return (await response.json()).accessToken as string;}
async function mutate(request:APIRequestContext,token:string,method:string,url:string,data:unknown):Promise<Record<string,any>>{const csrfToken=await csrf(request);const response=await request.fetch(url,{method,headers:{Authorization:`Bearer ${token}`,'X-XSRF-TOKEN':csrfToken},data});expect(response.ok(),`${method} ${url}: ${response.status()}`).toBe(true);return await response.json();}
async function login(page:Page,email:string,password:string,admin=false){await page.goto(admin?'/auth/admin-login':'/auth/login');await page.getByLabel('Adresse e-mail').fill(email);await page.getByLabel('Mot de passe').fill(password);await page.getByRole('button',{name:/Se connecter/}).click();await expect(page).toHaveURL(admin?/\/admin$/:/\/customer\/dashboard$/);}