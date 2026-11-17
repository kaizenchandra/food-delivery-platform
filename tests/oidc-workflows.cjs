const {chromium}=require('../frontend/node_modules/playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const BASE=process.env.WEB_URL||'http://localhost:3008';
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'chrome'});
 const contexts=[],browserErrors=[];
 async function login(username){
  const context=await browser.newContext();contexts.push(context);const page=await context.newPage();page.on("pageerror",e=>browserErrors.push(e.message));page.on("response",r=>{if(r.url().includes("/events"))console.log("SSE response",r.status())});
  await page.goto(BASE+'/oauth2/authorization/keycloak');await page.waitForURL(/8188.*openid-connect/);
  const auth=new URL(page.url());assert.equal(auth.searchParams.get('code_challenge_method'),'S256');assert.ok(auth.searchParams.get('state'));assert.ok(auth.searchParams.get('nonce'));
  await page.locator('#username').fill(username);await page.locator('#password').fill('local-demo');await page.locator('#kc-login').click();await page.waitForURL(BASE+'/',{timeout:20000});
  const response=await context.request.get(BASE+'/api/session');assert.equal(response.status(),200);const identity=await response.json();console.log('Authenticated',username,identity.role);
  const cookies=await context.cookies(BASE);assert.ok(cookies.some(c=>c.name==='SESSION'&&c.httpOnly&&c.sameSite==='Lax'));
  return {context,page,identity};
 }
 async function req(user,path,body,expected=200,headers={}){const response=await user.context.request.fetch(BASE+'/api/'+path,{method:body===undefined?'GET':'POST',headers:{...(body===undefined?{}:{[user.identity.csrfHeader]:user.identity.csrfToken}),...headers},...(body===undefined?{}:{data:body})});assert.equal(response.status(),expected,path+': '+await response.text());console.log('Verified',path,expected);return response;}
 const anonymous=await browser.newContext();contexts.push(anonymous);assert.equal((await anonymous.request.get(BASE+'/api/orders')).status(),401);
 assert.equal((await anonymous.request.get(BASE+'/api/orders',{headers:{Authorization:'Basic '+Buffer.from('customer:local-demo').toString('base64')}})).status(),401);
 const customer=await login('customer');assert.equal(customer.identity.role,'customer');
 // Local compatibility API remains covered separately; cart-address-checkout.cjs verifies the new production checkout UI.
 const previewMenu=await (await req(customer,'restaurants/1/menu')).json();const previewItem=previewMenu[0];
 const large=previewItem.groups.find(g=>g.name==='Portion').options.find(o=>o.name==='Large').id;const cheese=previewItem.groups.find(g=>g.name==='Extras').options.find(o=>o.name==='Extra cheese').id;
 const previewBody={item:previewItem.id,quantity:2,options:[large,cheese],instructions:'No chilli please',cutlery:true};
 const preview=await(await req(customer,'quote',previewBody)).json();assert.equal(preview.total,85700);
 const order=await(await req(customer,'checkout',previewBody,200,{'Idempotency-Key':crypto.randomUUID()})).json();assert.equal(order.total,85700);assert.equal(order.customer_name,customer.identity.subject);
 await customer.page.goto(BASE+'/orders/'+order.id);
 await customer.page.getByTestId('tracking-status').filter({hasText:'PLACED'}).waitFor();
 const choices=await (await req(customer,'orders/'+order.id)).json();
 assert.ok(choices.customization.includes('No chilli please'));
 const menu=await (await req(customer,'restaurants/1/menu')).json();const selected=menu[0];const portion=selected.groups.find(g=>g.name==='Portion').options[0].id;
 const body={item:selected.id,quantity:1,options:[portion],instructions:'',cutlery:false};
 const key=crypto.randomUUID();const a=await (await req(customer,'checkout',body,200,{'Idempotency-Key':key})).json();const b=await (await req(customer,'checkout',body,200,{'Idempotency-Key':key})).json();assert.equal(a.id,b.id);
 const concurrentKey=crypto.randomUUID();const concurrent=await Promise.all(Array.from({length:16},()=>req(customer,'checkout',body,200,{'Idempotency-Key':concurrentKey}).then(r=>r.json())));assert.equal(new Set(concurrent.map(o=>o.id)).size,1);
 await req(customer,'checkout',{...body,cutlery:true},409,{'Idempotency-Key':key});
 await req(customer,'quote',{...body,options:[]},400);await req(customer,'quote',{...body,options:[portion,portion]},400);
 const otherMenu=await (await req(customer,'restaurants/2/menu')).json();await req(customer,'quote',{...body,options:[otherMenu[0].groups[0].options[0].id]},400);
 assert.equal((await customer.context.request.post(BASE+'/api/quote',{data:body})).status(),403);
 const other=await login('other-customer');await req(other,'orders/'+order.id,undefined,404);await req(other,'orders/'+order.id+'/events',undefined,404);await req(other,'orders/'+order.id+'/actions',{action:'cancel'},403);await req(other,'logs',undefined,403);
 const restaurant=await login('restaurant'),driver=await login('driver'),wrongRestaurant=await login('other-restaurant'),wrongDriver=await login('other-driver');

 await req(wrongRestaurant,'orders/'+order.id+'/actions',{action:'accept'},403);
 await req(driver,'checkout',body,403,{'Idempotency-Key':crypto.randomUUID()});
 for(const action of ['accept','prepare','ready']){await req(restaurant,'orders/'+order.id+'/actions',{action});await customer.page.getByTestId('tracking-status').filter({hasText:action==='accept'?'ACCEPTED':action==='prepare'?'PREPARING':'READY'}).waitFor();}
 await req(driver,'orders/'+order.id+'/actions',{action:'assign'});await req(driver,'orders/'+order.id+'/actions',{action:'pickup'});
 await req(wrongDriver,'orders/'+order.id+'/location',{latitude:12.97,longitude:77.59,sampledAt:new Date().toISOString()},404);
 await req(driver,'orders/'+order.id+'/location',{latitude:NaN,longitude:77.59,sampledAt:new Date().toISOString()},400);
 await req(driver,'orders/'+order.id+'/location',{latitude:12.97,longitude:77.59,sampledAt:'2000-01-01T00:00:00Z'},400);
 await req(driver,'orders/'+order.id+'/location',{latitude:12.972,longitude:77.595,sampledAt:new Date().toISOString()});
 await customer.page.getByTestId('coordinates').filter({hasText:'12.97200'}).waitFor();await customer.page.locator('[data-order=\"'+order.id+'\"] .eyebrow').filter({hasText:'PICKED_UP'}).waitFor();
 await customer.page.screenshot({path:'/tmp/saffron-custom-tracking.png',fullPage:true});await customer.page.setViewportSize({width:390,height:844});await customer.page.screenshot({path:'/tmp/saffron-custom-tracking-mobile.png',fullPage:true});
 // Disconnect, update, reconnect with a durable cursor; recovered event must arrive through the real SSE route.
 const before=await (await req(customer,'orders/'+order.id)).json();await customer.page.getByRole('button',{name:'Close tracking'}).click();assert.ok(before.eta_minutes>0);
 await req(customer,'orders/'+order.id+'/events',undefined,400,{'Last-Event-ID':'invalid'});
 await req(driver,'orders/'+order.id+'/actions',{action:'deliver',otp:'wrong'},400);
 await req(driver,'orders/'+order.id+'/actions',{action:'deliver',otp:'246810'});
 const replay=await customer.page.evaluate(({id,version})=>new Promise((resolve,reject)=>{const source=new EventSource('/api/orders/'+id+'/events?after='+version);const timeout=setTimeout(()=>{source.close();reject(new Error('Replay timeout'))},10000);source.addEventListener('update',event=>{clearTimeout(timeout);source.close();resolve(JSON.parse(event.data))})}),{id:order.id,version:before.version});assert.equal(replay.state,'DELIVERED');assert.equal(replay.eta_minutes,0);
 await customer.page.locator('[data-order=\"'+order.id+'\"]').getByRole('button',{name:'Track order'}).click();await customer.page.getByTestId('tracking-status').filter({hasText:'DELIVERED'}).waitFor();
 await req(driver,'orders/'+order.id+'/location',{latitude:12.973,longitude:77.595,sampledAt:new Date().toISOString()},403);

 const admin=await login('admin');assert.equal(admin.identity.role,'admin');
 const ledger=await (await req(admin,'ledger')).json();assert.ok(ledger.some(row=>row.order_id===order.id&&row.kind==='RESTAURANT_PAYABLE'));
 let indexed=false;for(let attempt=0;attempt<20;attempt++){const logs=await (await req(admin,'logs?q='+order.id)).json();if(logs.hits?.hits?.length){indexed=true;break}await new Promise(resolve=>setTimeout(resolve,1000))}assert.ok(indexed,'OIDC administrator can investigate indexed order logs');
 const state=await customer.context.storageState();await customer.page.getByRole('button',{name:'Sign out',exact:true}).click();await customer.page.waitForURL(BASE+'/',{timeout:20000});assert.equal((await customer.context.request.get(BASE+'/api/session')).status(),401);
 const stolen=await browser.newContext({storageState:state});contexts.push(stolen);assert.equal((await stolen.request.get(BASE+'/api/orders')).status(),401);
 assert.deepEqual(browserErrors,[]);
 console.log('PASS OIDC code+PKCE, roles/ownership, HttpOnly session, CSRF, logout replay denial, customization quote/snapshot/idempotency, SSE state/GPS/replay, isolation');
 fs.writeFileSync('/tmp/saffron-oidc-evidence.json',JSON.stringify({orderId:order.id,amount:order.total,replayVersion:replay.version,result:'PASS'},null,2));
 await Promise.all(contexts.map(c=>c.close()));await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
