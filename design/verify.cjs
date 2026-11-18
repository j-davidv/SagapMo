// Run with PLAYWRIGHT_MODULE pointing to an existing playwright or playwright-core install.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const { pathToFileURL } = require('node:url');

(async () => {
  const browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{})});
  const page=await browser.newPage({viewport:{width:1440,height:1100},deviceScaleFactor:1});
  const errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.emulateMedia({reducedMotion:'reduce'});
  const output=path.join(__dirname,'screenshots');
  fs.mkdirSync(output,{recursive:true});
  await page.goto(pathToFileURL(path.join(__dirname,'index.html')).href);
  const click=selector=>page.locator(selector).click();
  const scenario=async name=>{await page.selectOption('#scenario',name);await page.waitForTimeout(60);};
  const close=async()=>{if(await page.locator('#sheet').evaluate(el=>el.open))await page.keyboard.press('Escape');};
  const log=[];
  const check=(name,ok)=>{assert.ok(ok,name);log.push(name);};

  check('Initial map has five located sample pins',await page.locator('.pin').count()===5);
  check('Floating bar contains two destinations and one recording action',await page.locator('.app-nav [data-view]').count()===2&&await page.locator('.app-nav #record-button').count()===1&&await page.locator('[data-action="record"]').count()===1);
  await page.screenshot({path:path.join(output,'desktop.png'),fullPage:true});
  await page.locator('#device').screenshot({path:path.join(output,'01-map.png')});
  await click('[data-filter="WIFI"]');check('Wi-Fi filter shows two located Wi-Fi samples',await page.locator('.pin').count()===2);
  await click('[data-view="readings"]');check('Filter follows navigation; all three Wi-Fi readings remain in list',await page.locator('.reading-row').count()===3);
  await click('.reading-row:first-child');check('Details show Wi-Fi timestamp limitation',await page.locator('#sheet').innerText().then(t=>t.includes('Wi-Fi measurement age unavailable')));
  await click('[data-action="show-map"]');check('Show on map selects and focuses the matching pin',await page.locator('.pin.selected').count()===1);
  await click('.pin.selected');await page.keyboard.press('Escape');check('Escape closes details',await page.locator('#sheet').evaluate(el=>!el.open));

  await scenario('readings');await page.locator('#device').screenshot({path:path.join(output,'02-readings.png')});
  await scenario('detail');await page.locator('#device').screenshot({path:path.join(output,'03-detail.png')});await close();
  await scenario('permission');await page.locator('#device').screenshot({path:path.join(output,'04-location-explainer.png')});
  await click('[data-action="skip-location"]');await page.waitForTimeout(1200);
  check('Skipping location saves one unlocated sample',await page.evaluate(()=>model.rows.length===7&&!!model.rows[0].reason));
  await click('[data-action="show-saved"]');check('Saved reading opens shared details',await page.locator('#sheet-title').innerText()==='Reading details');await close();

  await scenario('ready');await page.selectOption('#capture-type','MOBILE');await click('[data-filter="WIFI"]');
  await click('[data-action="record"]');await page.waitForTimeout(3100);
  check('Capture uses active connection independently of display filter',await page.evaluate(()=>model.rows.length===7&&model.rows[0].type==='MOBILE'));
  check('Save result explains hidden type',await page.locator('#sheet').innerText().then(t=>t.includes('filter hides this Mobile sample')));
  await click('[data-action="show-saved"]');check('Show saved reading reveals its hidden connection type',await page.evaluate(()=>model.filter==='MOBILE'&&model.view==='readings'));await close();

  await scenario('save-error');const candidateId=await page.evaluate(()=>model.candidate.id);
  await click('[data-action="retry-save"]');await page.waitForTimeout(850);
  check('Retry save preserves candidate ID and inserts once',await page.evaluate(id=>model.rows.filter(r=>r.id===id).length===1,candidateId));
  await page.evaluate(()=>commitCandidate());check('Duplicate completion creates no duplicate reading',await page.evaluate(()=>model.rows.length===7));await close();

  await scenario('locating');await click('[data-action="keep-browsing"]');await click('[data-view="readings"]');
  check('Screen switching retains capture and disables duplicate recording',await page.locator('#record-button').isDisabled());
  await page.waitForTimeout(3100);check('Capture completes after switching screens',await page.evaluate(()=>model.rows.length===7&&!model.busy));await close();

  for(const name of ['empty','filter-empty','map-loading','map-error','read-loading','read-error','permission','locating','saving','saved','saved-no-pin','no-network','vpn','signal-error','stale-signal','changed','interrupted']) {
    await scenario(name);check(`State renders: ${name}`,await page.locator('#app-content').innerText().then(t=>t.length>0));
    if(['map-error','saved-no-pin','empty','locating','save-error'].includes(name))await page.locator('#device').screenshot({path:path.join(output,`${name}.png`)});
    await close();
  }
  for(const reason of ['PERMISSION_DENIED','APPROXIMATE_ONLY','LOCATION_DISABLED','LOCATION_UNAVAILABLE','LOCATION_STALE','LOCATION_INACCURATE','LOCATION_TIMEOUT']) {
    await page.selectOption('#location-reason',reason);await scenario('saved-no-pin');
    check(`Missing-location reason: ${reason}`,await page.evaluate(r=>model.rows[0].reason===r,reason));await close();
  }
  await scenario('map-error');await click('[data-action="retry-map"]');check('Retry map restores normal map',await page.locator('.map-status').count()===0);
  await scenario('map-error');await click('[data-action="view-readings"]');check('Map failure leaves readings usable',await page.locator('.reading-row').count()===6);
  await scenario('read-error');await click('[data-action="retry-read"]');check('Read error retry restores list',await page.locator('.reading-row').count()===6);
  await scenario('filter-empty');await click('[data-action="all"]');check('Empty filter recovers with All',await page.locator('.reading-row').count()===3);
  await scenario('ready');await click('[data-action="campus"]');check('Campus overview control announces its action',await page.locator('#announcement').innerText().then(t=>t.includes('Campus overview restored')));
  await scenario('permission');await click('[data-action="allow-location"]');await click('[data-action="cancel-capture"]');check('Cancel capture creates no record',await page.evaluate(()=>model.rows.length===6&&!model.busy));
  await scenario('locating');await click('[data-action="finish-location"]');await click('[data-action="finish-save"]');await page.waitForTimeout(800);check('Manual progress preview completes save',await page.evaluate(()=>model.rows.length===7));await page.getByRole('button',{name:'Done',exact:true}).click();
  await scenario('signal-error');await click('[data-action="retry-capture"]');await page.waitForTimeout(3100);check('Signal retry starts a fresh successful operation',await page.evaluate(()=>model.rows.length===7));await close();
  await click('#reset');check('Reset restores six samples and first-use flow',await page.evaluate(()=>model.rows.length===6&&!model.permissionExplained));

  await click('#theme-toggle');check('Dark appearance changes token scheme',await page.locator('html').evaluate(el=>el.classList.contains('dark')));
  await page.waitForTimeout(250);await page.locator('#device').screenshot({path:path.join(output,'dark-map.png')});
  await scenario('readings');await page.locator('#device').screenshot({path:path.join(output,'dark-readings.png')});
  await click('#theme-toggle');await scenario('ready');await page.waitForTimeout(250);
  for(const width of [320,390,768,1280,1440]) {
    await page.setViewportSize({width,height:1000});
    check(`No document overflow at ${width}px`,await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
    check(`Floating controls fit with 48px targets at ${width}px`,await page.locator('.app-nav').evaluate(nav=>{
      const bar=nav.getBoundingClientRect(),device=nav.closest('.device').getBoundingClientRect();
      return bar.left>device.left&&bar.right<device.right&&[...nav.querySelectorAll('button')].every(button=>{
        const rect=button.getBoundingClientRect();
        return rect.width>=48&&rect.height>=48&&rect.left>=bar.left&&rect.right<=bar.right;
      });
    }));
    if(width===390)await page.screenshot({path:path.join(output,'mobile.png'),fullPage:true});
  }
  await page.setViewportSize({width:390,height:1000});
  await page.evaluate(()=>document.documentElement.style.fontSize='20px');
  check('Enlarged browser root text does not overflow page',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.evaluate(()=>document.documentElement.style.fontSize='');
  await page.emulateMedia({reducedMotion:'reduce'});
  await page.locator('#scenario').focus();await page.keyboard.press('Tab');
  check('Keyboard Tab reaches next labeled control',await page.locator('#capture-type').evaluate(el=>el===document.activeElement));
  const contrast=[];
  for(const dark of [false,true]) {
    await page.evaluate(value=>document.documentElement.classList.toggle('dark',value),dark);
    contrast.push(await page.evaluate(()=>{
      const style=getComputedStyle(document.documentElement);
      const luminance=color=>{
        const rgb=color.startsWith('#')?color.slice(1).match(/../g).map(v=>parseInt(v,16)):color.match(/[\d.]+/g).slice(0,3).map(Number);
        const c=rgb.map(v=>v/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4);
        return c[0]*.2126+c[1]*.7152+c[2]*.0722;
      };
      const ratio=(fg,bg)=>{const a=luminance(fg),b=luminance(bg);return +((Math.max(a,b)+.05)/(Math.min(a,b)+.05)).toFixed(2);};
      const pairs=[['body','--ink','--surface'],['secondary','--muted','--surface'],['secondary on subtle','--muted','--subtle'],['Good','--good','--good-bg'],['Fair','--fair','--fair-bg'],['Poor','--poor','--poor-bg'],['accent','--primary','--surface']];
      const values=pairs.map(([name,fg,bg])=>({name,ratio:ratio(style.getPropertyValue(fg).trim(),style.getPropertyValue(bg).trim())}));
      const button=getComputedStyle(document.querySelector('#record-button'));
      values.push({name:'primary button',ratio:ratio(button.color,button.backgroundColor)});
      return {theme:document.documentElement.classList.contains('dark')?'dark':'light',values};
    }));
  }
  check('Light and dark text/token pairs exceed 4.5:1',contrast.every(theme=>theme.values.every(pair=>pair.ratio>=4.5)));
  fs.writeFileSync(path.join(output,'contrast.json'),JSON.stringify(contrast,null,2));
  check('No runtime JavaScript errors',errors.length===0);
  fs.writeFileSync(path.join(output,'checks.json'),JSON.stringify({passed:log.length,checks:log,errors},null,2));
  console.log(JSON.stringify({passed:log.length,errors,screenshots:output},null,2));
  await browser.close();
})().catch(error=>{console.error(error);process.exitCode=1;});
