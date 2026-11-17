const {chromium}=require('../frontend/node_modules/playwright');
const assert=require('node:assert/strict'),fs=require('node:fs');
const BASE='http://localhost:3008',IAM='http://localhost:8188';
const gate=JSON.parse(fs.readFileSync('/tmp/saffron-provider-gate.json'));
const env=process.env.LOCAL_IAM_ADMIN_PASSWORD?process.env:Object.fromEntries(fs.readFileSync('/tmp/saffron-iam-admin.env','utf8').trim().split('\n').map(line=>{const i=line.indexOf('=');return [line.slice(0,i),line.slice(i+1)]}));
const contexts=[],errors=[];
let browser,adminToken,clientId,originalClient,fixtureDriver;
async function admin(path,method='GET',body){const response=await fetch(IAM+'/admin/realms/saffron/'+path,{method,headers:{Authorization:'Bearer '+adminToken,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});assert.ok(response.ok,'Keycloak administrator request failed: '+response.status);return response.status===204?null:response.json()}
async function control(path,body){return fetch(gate.url+path,{method:body===undefined?'GET':'POST',headers:{'X-Test-Control':gate.secret,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)})}
async function login(username){const context=await browser.newContext();contexts.push(context);const page=await context.newPage();page.on('pageerror',error=>errors.push(error.message));await page.goto(BASE+'/oauth2/authorization/keycloak');await page.locator('#username').fill(username);await page.locator('#password').fill('local-demo');await page.locator('#kc-login').click();await page.waitForURL(BASE+'/');const response=await context.request.get(BASE+'/api/session');assert.equal(response.status(),200);return {context,page,identity:await response.json()}}
async function api(user,path,body,status=200,extra={}){const response=await user.context.request.fetch(BASE+'/api/'+path,{method:body===undefined?'GET':'POST',headers:{[user.identity.csrfHeader]:user.identity.csrfToken,...extra},...(body===undefined?{}:{data:body})});assert.equal(response.status(),status,path+': '+await response.text());return response}
async function waitFor(check,message){for(let i=0;i<40;i++){if(await check())return;await new Promise(resolve=>setTimeout(resolve,250))}throw new Error(message)}
async function cleanupDriverFixture(driver){for(const order of await (await api(driver,'orders')).json()){if(!['PICKED_UP','ASSIGNED'].includes(order.state))continue;const details=await (await api(driver,'orders/'+order.id)).json();if(JSON.parse(details.customization).instructions!=='Routing/revocation acceptance fixture')continue;await api(driver,'orders/'+order.id+'/actions',{action:order.state==='PICKED_UP'?'deliver':'release',otp:'246810'});}}
(async()=>{
 browser=await chromium.launch({headless:true,channel:'chrome'});
 const tokenResponse=await fetch(IAM+'/realms/master/protocol/openid-connect/token',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'password',client_id:'admin-cli',username:'local-operator',password:env.LOCAL_IAM_ADMIN_PASSWORD})});assert.equal(tokenResponse.status,200);adminToken=(await tokenResponse.json()).access_token;
 const clients=await admin('clients?clientId=saffron-web');clientId=clients[0].id;originalClient=await admin('clients/'+clientId);
 // The relay is loopback-only, secret-protected, and validates via the real API. No token leaves process memory.
 await admin('clients/'+clientId,'PUT',{...originalClient,attributes:{...originalClient.attributes,'backchannel.logout.url':'http://host.docker.internal:5090'+gate.relayPath}});
 await control('/__mode',{mode:'live'});
 const beforeA=new Set((await admin('users/00000000-0000-0000-0000-000000000001/sessions')).map(s=>s.id));
 const customer=await login('customer');const providerSubject=customer.identity.subject;
 const sessionsA=await admin('users/'+providerSubject+'/sessions');const sidA=sessionsA.find(s=>!beforeA.has(s.id))?.id;assert.ok(sidA);
 const sibling=await login('customer');const unrelated=await login('other-customer');
 const menu=await (await api(customer,'restaurants/1/menu')).json();const meal=menu[0];const option=meal.groups.find(g=>g.min_choices===1).options.find(o=>o.available).id;
 const placed=await (await api(customer,'checkout',{item:meal.id,quantity:1,options:[option],instructions:'Routing/revocation acceptance fixture',cutlery:false},200,{'Idempotency-Key':crypto.randomUUID()})).json();
 await customer.page.getByRole('button',{name:'Orders',exact:true}).click();await customer.page.locator('[data-order="'+placed.id+'"]').getByRole('button',{name:'Track order'}).click();
 await customer.page.getByTestId('route-mode').filter({hasText:'Road route · OSRM'}).waitFor({timeout:15000});
 const road=await (await api(customer,'orders/'+placed.id+'/route')).json();assert.equal(road.route.mode,'ROAD');assert.ok(road.route.geometry.length>2);assert.ok(road.route.distanceMeters>0);assert.ok(road.route.steps.length>0);assert.equal(road.destinationKind,'DEMO_CUSTOMER_ADDRESS');
 await customer.page.getByRole('img',{name:'Road route map'}).waitFor();assert.ok(await customer.page.locator('.leaflet-overlay-pane path').count()>0);
 await api(unrelated,'orders/'+placed.id+'/route',undefined,404);
 await customer.page.getByLabel('Use local simulator',{exact:true}).check();await customer.page.getByTestId('route-mode').filter({hasText:'Local simulator'}).waitFor();await customer.page.getByLabel('Use local simulator',{exact:true}).uncheck();await customer.page.getByTestId('route-mode').filter({hasText:'Road route'}).waitFor();
 const restaurant=await login('restaurant'),driver=await login('driver');fixtureDriver=driver;await cleanupDriverFixture(driver);
 for(const action of ['accept','prepare','ready'])await api(restaurant,'orders/'+placed.id+'/actions',{action});
 await api(driver,'orders/'+placed.id+'/actions',{action:'assign'});await api(driver,'orders/'+placed.id+'/actions',{action:'pickup'});
 await control('/__mode',{mode:'outage'});
 await api(driver,'orders/'+placed.id+'/location',{latitude:12.9721,longitude:77.5951,sampledAt:new Date().toISOString()});
 await customer.page.getByTestId('route-mode').filter({hasText:'Local simulator'}).waitFor({timeout:10000});
 const fallback=await (await api(customer,'orders/'+placed.id+'/route')).json();assert.equal(fallback.route.mode,'SIMULATOR');assert.ok(['PROVIDER_UNAVAILABLE','PROVIDER_BACKOFF'].includes(fallback.route.reason));assert.equal(fallback.route.geometry.length,2);
 await control('/__mode',{mode:'live'});await new Promise(resolve=>setTimeout(resolve,5500));
 await api(driver,'orders/'+placed.id+'/location',{latitude:12.9724,longitude:77.5954,sampledAt:new Date().toISOString()});
 await customer.page.getByTestId('route-mode').filter({hasText:'Road route · OSRM'}).waitFor({timeout:15000});
 await customer.page.getByLabel('Show OpenStreetMap street tiles',{exact:true}).check();await customer.page.locator('.leaflet-tile-loaded').first().waitFor({timeout:15000});await customer.page.screenshot({path:'/tmp/saffron-road-map.png',fullPage:false});
 await customer.page.setViewportSize({width:390,height:844});await customer.page.screenshot({path:'/tmp/saffron-road-map-mobile.png',fullPage:false});
 // Provider administrator revokes exactly one provider sid. No application logout button is used.
 const oldCookies=await customer.context.storageState();const started=Date.now();await admin('sessions/'+sidA,'DELETE');
 await waitFor(async()=> (await customer.context.request.get(BASE+'/api/session')).status()===401,'Provider-initiated logout did not revoke the browser session');
 await customer.page.getByRole('link',{name:'Sign in securely'}).waitFor({timeout:10000});assert.equal(await customer.page.locator('[aria-label="Live order tracking"]').count(),0);const revocationUiDelay=Date.now()-started;
 assert.equal((await sibling.context.request.get(BASE+'/api/session')).status(),200,'Other provider sid must remain active');assert.equal((await unrelated.context.request.get(BASE+'/api/session')).status(),200);
 const stolen=await browser.newContext({storageState:oldCookies});contexts.push(stolen);assert.equal((await stolen.request.get(BASE+'/api/orders')).status(),401);
 const stats=await (await control('/__stats')).json();assert.ok(stats.logout_deliveries>0);assert.equal(stats.last_status,200);
 const fresh=await login('customer');const replay=await control('/__replay',{});assert.equal(replay.status,200);assert.equal((await fresh.context.request.get(BASE+'/api/session')).status(),200,'Old logout retry must not revoke a new provider sid');
 assert.equal((await control('/__tamper',{})).status,400);assert.equal((await fresh.context.request.get(BASE+'/api/session')).status(),200);
 const unsigned=await customer.context.request.post('http://localhost:8088/logout/connect/back-channel/keycloak',{form:{logout_token:'not.a.valid.jwt'}});assert.equal(unsigned.status(),400);
 // User-wide administrative logout revokes all remaining sessions for that subject.
 await admin('clients/'+clientId,'PUT',{...originalClient,attributes:{...originalClient.attributes,'backchannel.logout.url':'http://host.docker.internal:5090'+gate.relayPath,'backchannel.logout.session.required':'false'}});
 await admin('users/'+providerSubject+'/logout','POST');await waitFor(async()=> (await sibling.context.request.get(BASE+'/api/session')).status()===401,'Subject session remained active after provider-wide logout');await waitFor(async()=> (await fresh.context.request.get(BASE+'/api/session')).status()===401,'Fresh sibling session remained active after subject-wide logout');assert.equal((await unrelated.context.request.get(BASE+'/api/session')).status(),200);
 await api(driver,'orders/'+placed.id+'/actions',{action:'deliver',otp:'246810'});
 assert.deepEqual(errors,[]);
 const evidence={result:'PASS',orderId:placed.id,realProvider:'OSRM live via loopback fault gate',distanceMeters:road.route.distanceMeters,roadGeometryPoints:road.route.geometry.length,providerLogoutUiDelayMs:revocationUiDelay,checks:['road route and optional street tiles','explicit simulator','provider outage fallback and recovery','route ownership denial','provider sid revocation and SSE closure','other sid unaffected','cookie replay denied','logout-token replay idempotent','tampered signature denied','provider user-wide logout']};
 fs.writeFileSync('/tmp/saffron-revocation-routing.json',JSON.stringify(evidence,null,2));console.log('PASS live OSRM road map, simulator/outage/recovery, Keycloak provider revocation, SSE closure, session isolation and token replay/signature checks');
})().catch(error=>{console.error(error);process.exitCode=1}).finally(async()=>{try{if(fixtureDriver)await cleanupDriverFixture(fixtureDriver);if(originalClient)await admin('clients/'+clientId,'PUT',originalClient);await control('/__mode',{mode:'live'})}catch(error){console.error('Could not restore local test configuration:',error.message)}await Promise.all(contexts.map(context=>context.close()));if(browser)await browser.close()});
