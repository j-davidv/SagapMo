'use strict';

// This is an in-memory interaction reference, not the Android capture or persistence layer.
const icons = {
  home:'<path d="m4 10 8-7 8 7v11h-6v-7h-4v7H4Z"/>',
  capture:'<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5.5"/><path d="M15 15.5 12 12V9"/>',
  settings:'<path d="m9 3-.5 2-2 1-2-.5-2 3 1.5 1.5v3L2.5 15l2 3 2-.5 2 1L9 21h6l.5-2.5 2-1 2 .5 2-3-1.5-2v-3L21.5 9l-2-3-2 .5-2-1L15 3Z"/><circle cx="12" cy="12" r="3"/>',
  wifi:'<path d="M3 8.5a14 14 0 0 1 18 0M6 12a9 9 0 0 1 12 0m-9 3.5a4 4 0 0 1 6 0"/><circle cx="12" cy="19" r="1" fill="currentColor" stroke="none"/>',
  mobile:'<path d="M4 20v-3m5 3v-7m5 7V9m5 11V4" stroke-width="2.5"/>',
  map:'<path d="m3 5 6-2 6 2 6-2v16l-6 2-6-2-6 2zm6-2v16m6-14v16"/>',
  list:'<path d="M8 6h13M8 12h13M8 18h13"/><circle cx="3" cy="6" r="1"/><circle cx="3" cy="12" r="1"/><circle cx="3" cy="18" r="1"/>',
  pin:'<path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z"/><circle cx="12" cy="10" r="2.5"/>',
  noPin:'<path d="M17 16c-2 3-5 5-5 5S5 15 5 10a7 7 0 0 1 2-5m4-2a7 7 0 0 1 8 7v1M3 3l18 18"/>',
  record:'<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="3.5"/>',
  close:'<path d="m6 6 12 12M6 18 18 6"/>',
  check:'<path d="m5 12 4 4L19 6"/>',
  target:'<path d="M12 2v3m0 14v3M2 12h3m14 0h3"/><circle cx="12" cy="12" r="7"/><circle cx="12" cy="12" r="2.5"/>',
  info:'<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v.5"/>',
  warning:'<path d="m12 3 10 18H2Zm0 6v5m0 3v.5"/>',
  back:'<path d="m14 6-6 6 6 6"/>',
};
const icon = name => `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${icons[name] || icons.info}</svg>`;
const reasons = {
  PERMISSION_DENIED:'Location permission unavailable. Saved without a pin.',
  APPROXIMATE_ONLY:'Precise location is needed for a campus pin. Saved without a pin.',
  LOCATION_DISABLED:'Location services are off. Saved without a pin.',
  LOCATION_UNAVAILABLE:'Location unavailable. Saved without a pin.',
  LOCATION_STALE:'Location was too old. Saved without a pin.',
  LOCATION_INACCURATE:'Location was too imprecise. Saved without a pin.',
  LOCATION_TIMEOUT:'Location timed out. Saved without a pin.',
};
const samples = [
  {id:'sample-1',type:'WIFI',raw:-62,rating:'Good',time:'2026-11-17T10:42:00+08:00',x:42,y:35,lat:13.78422,lon:121.07431,accuracy:8,locationAge:5,signalAge:null},
  {id:'sample-2',type:'MOBILE',raw:2,rating:'Fair',time:'2026-11-17T10:35:00+08:00',x:70,y:53,lat:13.78390,lon:121.07410,accuracy:14,locationAge:9,signalAge:12},
  {id:'sample-3',type:'WIFI',raw:-79,rating:'Poor',time:'2026-11-17T10:28:00+08:00',reason:'APPROXIMATE_ONLY'},
  {id:'sample-4',type:'MOBILE',raw:4,rating:'Good',time:'2026-11-17T10:15:00+08:00',x:26,y:60,lat:13.78455,lon:121.07460,accuracy:10,locationAge:4,signalAge:4},
  {id:'sample-5',type:'WIFI',raw:-71,rating:'Fair',time:'2026-11-16T15:20:00+08:00',x:59,y:75,lat:13.78415,lon:121.07425,accuracy:18,locationAge:7,signalAge:null},
  {id:'sample-6',type:'MOBILE',raw:1,rating:'Poor',time:'2026-11-16T14:04:00+08:00',x:64,y:26,lat:13.78410,lon:121.07440,accuracy:12,locationAge:3,signalAge:8},
];
const model = { rows:samples.map(r=>({...r})), view:'map', filter:'ALL', scenario:'ready', selected:null, candidate:null, busy:false, permissionExplained:false, timer:null, lastTrigger:null };
const $ = id => document.getElementById(id);
const typeName = type => type === 'WIFI' ? 'Wi-Fi' : 'Mobile';
const typeIcon = type => type === 'WIFI' ? 'wifi' : 'mobile';
const rawValue = r => r.type === 'WIFI' ? `${r.raw} dBm` : `Level ${r.raw}/4`;
const rating = r => `<span class="rating ${r.rating.toLowerCase()}"><span class="rating-dot ${r.rating.toLowerCase()}"></span>${r.rating}</span>`;
const fmtTime = r => new Intl.DateTimeFormat('en-PH',{timeZone:'Asia/Taipei',hour:'numeric',minute:'2-digit'}).format(new Date(r.time));
const fmtDate = r => new Intl.DateTimeFormat('en-PH',{timeZone:'Asia/Taipei',day:'numeric',month:'long',year:'numeric',hour:'numeric',minute:'2-digit',second:'2-digit'}).format(new Date(r.time));
const filteredRows = () => model.rows.filter(r=>model.filter==='ALL'||r.type===model.filter);
const announce = text => { $('announcement').textContent=text; };

function filters() {
  return `<div class="filters" role="group" aria-label="Filter saved readings">${['ALL','WIFI','MOBILE'].map(type=>`<button class="filter" data-filter="${type}" aria-pressed="${model.filter===type}">${type==='ALL'?'':icon(typeIcon(type))}${type==='ALL'?'All':typeName(type)}<span class="filter-count">${model.rows.filter(r=>type==='ALL'||r.type===type).length}</span></button>`).join('')}</div>`;
}

function mapGeometry() {
  return `<svg viewBox="0 0 412 470" preserveAspectRatio="xMidYMid slice" role="img" aria-label="Illustrative campus basemap, not verified geography">
    <rect width="412" height="470" fill="var(--map)"/>
    <path d="M-20 163 145 156 205 111 432 112M97-20 105 120 122 260 80 490M334-20 322 170 342 315 430 360M-20 343 149 329 253 357 433 428" class="map-road" stroke-width="17" fill="none"/>
    <path d="M129 212h183M199 115l-5 225M120 276l93 20 117-39M210 357l-9 130" class="map-road" stroke-width="7" fill="none"/>
    <g class="map-block"><rect x="133" y="170" width="55" height="31" rx="3"/><rect x="213" y="143" width="88" height="46" rx="3"/><rect x="209" y="224" width="44" height="37" rx="3"/><rect x="269" y="219" width="38" height="49" rx="3"/><rect x="28" y="183" width="55" height="61" rx="3"/><rect x="30" y="263" width="54" height="41" rx="3"/><rect x="354" y="184" width="44" height="90" rx="3"/><rect x="119" y="368" width="57" height="55" rx="3"/><rect x="232" y="391" width="67" height="36" rx="3"/><rect x="244" y="48" width="55" height="42" rx="3"/><rect x="140" y="60" width="57" height="50" rx="3"/></g>
    <rect x="144" y="287" width="159" height="59" rx="29" fill="var(--map-green)"/>
    <rect x="153" y="295" width="141" height="43" rx="21" fill="none" stroke="var(--surface)" stroke-width="1.5"/>
    <text x="223" y="322" text-anchor="middle" class="map-label">Campus grounds</text>
  </svg>`;
}

function renderMap() {
  const rows=filteredRows(), located=rows.filter(r=>!r.reason);
  const pins=located.map(r=>`<button class="pin ${r.rating.toLowerCase()} ${model.selected===r.id?'selected':''}" style="left:${r.x}%;top:${r.y}%" data-reading="${r.id}" aria-label="${typeName(r.type)}, ${r.rating}, ${rawValue(r)}, ${fmtTime(r)}"><span class="pin-symbol">${icon(typeIcon(r.type))}</span><span class="pin-label">${r.rating}</span></button>`).join('');
  let state='';
  if(!rows.length) state=`<div class="map-status"><h2>${model.rows.length?`No ${typeName(model.filter)} readings`:'No readings yet'}</h2><p>${model.rows.length?'This filter has no samples. Your other readings are still saved.':'Record your first signal sample. A usable outdoor location adds a pin.'}</p>${model.rows.length?'<button class="text-button" data-action="all">Show all readings</button>':''}</div>`;
  if(model.scenario==='map-error') state='<div class="map-status" role="status"><h2>Map could not load</h2><p>Your saved readings are still available. You can record a valid signal even if map tiles fail.</p><div class="actions"><button class="secondary-button" data-action="retry-map">Retry map</button><button class="text-button" data-action="view-readings">View readings</button></div></div>';
  if(model.scenario==='map-loading') state='<div class="map-loading-label" role="status">Loading map… Readings are still available.</div>';
  return `<section class="map-view" aria-label="Campus signal samples"><div class="map-canvas">${mapGeometry()}</div>${model.scenario==='map-loading'?'':pins}<div class="map-top">${filters()}<div class="map-note">${located.length} pinned · ${rows.length-located.length} without a pin</div></div><button class="campus-reset" data-action="campus" aria-label="Return to campus overview">${icon('target')}</button>${state}<div class="map-legend" aria-label="Sample ratings"><span class="legend-item"><i class="rating-dot"></i>Good</span><span class="legend-item"><i class="rating-dot fair"></i>Fair</span><span class="legend-item"><i class="rating-dot poor"></i>Poor</span></div><div class="map-attribution"><span>Reference map</span> · Production: OpenFreeMap · OpenMapTiles · © OpenStreetMap</div></section>`;
}

function renderList() {
  const rows=filteredRows();
  let content;
  if(model.scenario==='read-loading') content='<div class="skeleton" role="status" aria-label="Loading saved readings"><span></span><span></span><span></span><span></span><span></span><span></span></div>';
  else if(model.scenario==='read-error') content=`<div class="state-block" role="status">${icon('warning')}<h2>Readings could not load</h2><p>Your collection has not been reset. Try loading it again.</p><button class="secondary-button" data-action="retry-read">Retry readings</button></div>`;
  else if(!rows.length) content=`<div class="state-block">${icon('list')}<h2>${model.rows.length?`No ${typeName(model.filter)} readings`:'No readings yet'}</h2><p>${model.rows.length?'Try All to see your other saved samples.':'Record a signal sample. Find it here later, even without a pin or internet access.'}</p>${model.rows.length?'<button class="secondary-button" data-action="all">Show all readings</button>':''}</div>`;
  else content=`<div class="reading-list" aria-label="Saved samples">${rows.map(r=>`<button class="reading-row" data-reading="${r.id}"><span class="row-top"><span class="connection-icon">${icon(typeIcon(r.type))}</span><span class="row-main"><strong>${typeName(r.type)}</strong><time datetime="${r.time}">${r.time.includes('2026-11-17')?'Today':'16 Nov'} · ${fmtTime(r)}</time></span><span class="row-value">${rating(r)}<b>${rawValue(r)}</b></span></span><span class="row-location ${r.reason?'no-pin':''}">${icon(r.reason?'noPin':'pin')}<span>${r.reason?`No pin · ${reasons[r.reason].replace(' Saved without a pin.','')}`:`On map · Accuracy ±${r.accuracy} m`}</span></span></button>`).join('')}</div>`;
  return `<div class="list-header"><div class="list-title-row"><h2>Your readings</h2><span>${model.rows.length} saved</span></div>${filters()}</div><div class="list-meta"><span>Newest first</span><span>Saved on this phone</span></div>${content}`;
}

function renderPanel() {
  const type=typeName($('capture-type').value);
  return `<div class="record-hint">${icon('info')}<div><strong>${model.view==='map'?'Pins are individual samples':'Your readings stay with you'}</strong><small>${model.view==='map'?'Areas without pins have not been sampled.':'Stored on this phone. No account needed.'}</small></div></div><span class="sr-only">Simulated active connection: ${type}. Filters do not choose the connection.</span>`;
}

function render() {
  $('app-content').innerHTML=model.view==='map'?renderMap():renderList();
  $('record-panel').innerHTML=renderPanel();
  $('record-button').disabled=model.busy;
  $('record-button').setAttribute('aria-label',model.busy?'Recording…':'Record reading');
  $('record-button').title=model.busy?'Recording…':'Record reading';
  document.querySelectorAll('[data-view]').forEach(b=>{ if(b.dataset.view===model.view)b.setAttribute('aria-current','page'); else b.removeAttribute('aria-current'); });
}

function closeSheet() {
  $('sheet').close();
  const target=model.lastTrigger;
  if(target?.isConnected) target.focus({preventScroll:true});
  else document.querySelector('.app-nav [aria-current="page"]')?.focus({preventScroll:true});
}

function positionSheet() {
  if(!$('sheet').open)return;
  const rect=$('device').getBoundingClientRect(), sheet=$('sheet');
  sheet.style.width=`${rect.width-2}px`;
  sheet.style.maxHeight=`${Math.min(740,Math.max(180,Math.min(rect.bottom,window.innerHeight)-Math.max(rect.top+34,8)-10))}px`;
  sheet.style.left=`${rect.left+1}px`;
  sheet.style.top=`${Math.min(rect.bottom-1,window.innerHeight-8)-sheet.getBoundingClientRect().height}px`;
}

function openSheet(title,body,{closable=true}={}) {
  const sheet=$('sheet');
  if(!sheet.open) model.lastTrigger=document.activeElement;
  $('sheet-content').innerHTML=`<div class="sheet-inner"><div class="sheet-handle" aria-hidden="true"></div><div class="sheet-toolbar"><h2 id="sheet-title" tabindex="-1">${title}</h2>${closable?`<button class="icon-button" data-action="close" aria-label="Close ${title.toLowerCase()}">${icon('close')}</button>`:''}</div>${body}</div>`;
  if(!sheet.open)sheet.showModal();
  positionSheet();
  $('sheet-title').focus({preventScroll:true});
}

function showDetails(r) {
  model.selected=r.id;
  const facts=r.reason
    ?`<div><dt>Map pin</dt><dd>Not available</dd></div>`
    :`<div><dt>Coordinates</dt><dd>${r.lat.toFixed(5)}, ${r.lon.toFixed(5)}</dd></div><div><dt>Reported accuracy</dt><dd>±${r.accuracy} metres</dd></div><div><dt>Location age at capture</dt><dd>${r.locationAge} seconds</dd></div>`;
  openSheet('Reading details',`<div class="detail-overview"><span class="connection-icon">${icon(typeIcon(r.type))}</span><div><strong>${typeName(r.type)}</strong><span style="font-size:12px;color:var(--muted)">Saved sample</span></div></div><div class="detail-value">${r.type==='WIFI'?`${r.raw} <span>dBm</span>`:`${r.raw}<span> / 4 Android level</span>`}</div>${rating(r)}<p class="detail-date">${fmtDate(r)}<br>Local time · UTC+08:00</p><dl class="detail-data">${facts}<div><dt>Signal age at capture</dt><dd>${r.type==='WIFI'?'Unavailable for Wi-Fi':`${r.signalAge} seconds`}</dd></div></dl>${r.reason?`<div class="info-box warning"><strong>Saved without a pin</strong>${reasons[r.reason]}</div><p class="sheet-copy">This reading stays in your list. A later location will belong to a new reading.</p>`:''}${r.type==='WIFI'?'<p class="sheet-copy">Wi-Fi measurement age unavailable. Android does not provide a timestamp for this RSSI value.</p>':''}${r.reason?'<button class="primary-button" data-action="close">Back to readings</button>':`<button class="primary-button" data-action="show-map" data-id="${r.id}">${icon('map')}Show on map</button>`}`);
}

function newCandidate(withoutPin=false) {
  const type=$('capture-type').value;
  return {id:`demo-${crypto.randomUUID()}`,type,raw:type==='WIFI'?-64:3,rating:'Good',time:'2026-11-17T10:43:00+08:00',signalAge:type==='WIFI'?null:6,...(withoutPin?{reason:$('location-reason').value}:{x:48,y:48,lat:13.78421,lon:121.07428,accuracy:9,locationAge:3})};
}

function commitCandidate() {
  const candidate=model.candidate;
  if(!candidate)throw new Error('No candidate to save');
  if(!model.rows.some(r=>r.id===candidate.id)) model.rows.unshift(candidate);
  model.rows.sort((a,b)=>b.time.localeCompare(a.time)||b.id.localeCompare(a.id));
  model.busy=false;
  render();
  showSaved(candidate);
}

function showSaved(r) {
  const hidden=model.filter!=='ALL'&&model.filter!==r.type;
  openSheet('Reading saved',`<div class="saved-heading">${icon('check')}<p class="result-summary"><strong>${typeName(r.type)} · ${r.rating}</strong><br>${rawValue(r)} · ${fmtTime(r)}</p></div><div class="info-box ${r.reason?'warning':''}"><strong>${r.reason?'Saved without a pin':'Saved with a map pin'}</strong>${r.reason?reasons[r.reason]:'Your reading is in the list and on the map.'}</div>${hidden?`<p class="sheet-copy">Your ${typeName(model.filter)} filter hides this ${typeName(r.type)} sample. Show saved reading will reveal it.</p>`:''}<button class="primary-button" data-action="show-saved" data-id="${r.id}">Show saved reading</button><button class="text-button" data-action="close">Done</button>`);
  announce(`Reading saved. ${typeName(r.type)}, ${r.rating}, ${rawValue(r)}. ${r.reason?'Saved without a pin.':'Map pin available.'}`);
}

function showPermission() {
  openSheet('Add a pin to your reading',`<p class="sheet-copy">Precise location helps place your outdoor sample on the campus map.</p><div class="info-box"><strong>You can still save without location</strong>A valid signal reading stays in your list, even if you skip location or Android cannot get a usable fix.</div><p class="sheet-copy">Location is used while recording. Android will ask you to choose an access level next.</p><button class="primary-button" data-action="allow-location">Continue to location</button><button class="text-button" data-action="skip-location">Save without a pin</button><p class="sheet-progress">Prototype: this previews the flow; it does not request browser permissions.</p>`);
}

function showProgress(phase,manual=false) {
  const saving=phase==='saving';
  openSheet(saving?'Saving reading':'Getting location',`<div class="${saving?'saving':''}"><p class="sheet-copy">${saving?'Your signal is ready. Wait for confirmation before leaving the app.':'Stay at your sampling spot until recording finishes. Location can take up to 10 seconds.'}</p><div class="progress-track" aria-hidden="true"><span></span></div><ol class="progress-steps"><li class="${saving?'done':'current'}"><span class="step-mark">${saving?icon('check'):'1'}</span>Get an outdoor location</li><li class="${saving?'done':''}"><span class="step-mark">${saving?icon('check'):'2'}</span>Read the active connection</li><li class="${saving?'current':''}"><span class="step-mark">3</span>Save one reading</li></ol><p class="sheet-progress" role="status">${saving?'Saving reading…':'Getting location…'}</p>${manual?`<button class="secondary-button full" data-action="${saving?'finish-save':'finish-location'}">Preview next step</button>`:''}<button class="text-button" data-action="keep-browsing">Keep browsing</button>${saving?'':'<button class="text-button" data-action="cancel-capture">Cancel recording</button>'}</div>`,{closable:!saving});
  announce(saving?'Saving reading':'Getting location. Stay at your sampling spot.');
}

function beginCapture(withoutPin=false) {
  if(model.busy)return;
  model.permissionExplained=true;
  model.busy=true;
  model.candidate=newCandidate(withoutPin);
  render();
  if(withoutPin){ finishLocation(); return; }
  showProgress('locating');
  model.timer=setTimeout(finishLocation,1800);
}

function finishLocation(manual=false) {
  clearTimeout(model.timer);
  showProgress('saving',manual);
  if(!manual)model.timer=setTimeout(commitCandidate,1000);
}

const errors = {
  'no-network':['No supported connection','Connect to Wi-Fi or mobile data, then try recording again. Saved readings are still available.','Retry reading'],
  vpn:['Disconnect your VPN','The pilot needs a Wi-Fi or Mobile connection it can identify reliably. Disconnect the VPN in Android, then retry.','Retry reading'],
  'signal-error':['Signal unavailable','Android did not provide a valid signal value. Check your connection and try again. No reading was saved.','Retry reading'],
  'stale-signal':['Mobile signal too old','The available mobile measurement is older than 60 seconds. Wait for an updated signal, then retry. No reading was saved.','Retry reading'],
  changed:['Connection changed. Try again.','Your connection changed while the signal was being read. No reading was saved.','Retry reading'],
  interrupted:['Recording interrupted','The app left the foreground before saving began. Return to your sampling spot and record a new reading.','Record again'],
  'save-error':['Reading could not be saved','Your measured sample is still held for this attempt. Retry saving it without taking a new measurement. Closing the app may lose this unsaved sample.','Retry save'],
};
function showError(which) {
  const [title,copy,action]=errors[which];
  openSheet(title,`${icon('warning')}<p class="sheet-copy">${copy}</p><button class="primary-button" data-action="${which==='save-error'?'retry-save':'retry-capture'}">${action}</button><button class="text-button" data-action="close">Back to ${model.view==='map'?'map':'readings'}</button>`);
  announce(title);
}

function setScenario(scenario) {
  clearTimeout(model.timer);
  closeSheet();
  model.busy=false; model.candidate=null; model.selected=null;
  model.rows=samples.map(r=>({...r})); model.filter='ALL'; model.view='map'; model.scenario=scenario;
  $('scenario').value=scenario;
  if(['readings','filter-empty','read-loading','read-error'].includes(scenario))model.view='readings';
  if(scenario==='empty')model.rows=[];
  if(scenario==='filter-empty'){model.rows=model.rows.filter(r=>r.type==='WIFI');model.filter='MOBILE';}
  render();
  if(scenario==='detail')showDetails(model.rows[0]);
  if(scenario==='permission')showPermission();
  if(scenario==='locating'||scenario==='saving') { model.busy=true; model.candidate=newCandidate(); render(); showProgress(scenario,true); }
  if(scenario==='saved'||scenario==='saved-no-pin'){model.candidate=newCandidate(scenario==='saved-no-pin');commitCandidate();}
  if(scenario==='save-error'){model.candidate=newCandidate();showError(scenario);}
  else if(errors[scenario])showError(scenario);
  announce(`Preview: ${$('scenario').selectedOptions[0].textContent}`);
}

function changeView(view) {
  model.view=view;
  render();
  announce(view==='map'?'Map':'Your readings');
}

document.addEventListener('click',event=>{
  const button=event.target.closest('button');
  if(!button||button.disabled)return;
  if(button.dataset.filter){
    model.filter=button.dataset.filter;render();
    document.querySelector(`[data-filter="${model.filter}"]`)?.focus({preventScroll:true});
    announce(`${model.filter==='ALL'?'All':typeName(model.filter)} filter. ${filteredRows().length} readings.`);
  }
  if(button.dataset.view) changeView(button.dataset.view);
  if(button.dataset.reading)showDetails(model.rows.find(r=>r.id===button.dataset.reading));
  const action=button.dataset.action;
  if(!action)return;
  if(action==='settings')openSheet('Settings',`<button class="secondary-button full" data-action="toggle-appearance" aria-pressed="${document.documentElement.classList.contains('dark')}">Dark appearance</button>`);
  if(action==='toggle-appearance'){
    $('theme-toggle').click();
    button.setAttribute('aria-pressed',String(document.documentElement.classList.contains('dark')));
  }
  if(action==='close'){
    if(model.busy){clearTimeout(model.timer);model.busy=false;model.candidate=null;render();announce('Recording cancelled. No reading was saved.');}
    closeSheet();
  }
  if(action==='keep-browsing'){
    if(model.scenario==='locating')model.timer=setTimeout(finishLocation,1800);
    if(model.scenario==='saving')model.timer=setTimeout(commitCandidate,1000);
    closeSheet();
  }
  if(action==='record'){
    if(errors[model.scenario])showError(model.scenario);
    else if(!model.permissionExplained)showPermission();
    else beginCapture();
  }
  if(action==='allow-location')beginCapture();
  if(action==='skip-location')beginCapture(true);
  if(action==='finish-location')finishLocation(true);
  if(action==='finish-save'||action==='retry-save'){
    model.busy=true;render();showProgress('saving');model.timer=setTimeout(commitCandidate,650);
  }
  if(action==='cancel-capture'){
    clearTimeout(model.timer);model.busy=false;model.candidate=null;render();closeSheet();announce('Recording cancelled. No reading was saved.');
  }
  if(action==='retry-capture'){model.scenario='ready';model.busy=false;beginCapture();}
  if(action==='all'){model.filter='ALL';render();announce('Showing all readings.');}
  if(action==='view-readings'){changeView('readings');}
  if(action==='retry-map'||action==='retry-read'){
    model.scenario='ready';render();announce(action==='retry-map'?'Map preview restored.':'Saved readings loaded.');
  }
  if(action==='campus'){
    model.selected=null;render();announce('Campus overview restored. This reference uses a fixed schematic map.');
  }
  if(action==='show-map'){
    const r=model.rows.find(row=>row.id===button.dataset.id);
    model.selected=r.id;if(model.filter!=='ALL'&&model.filter!==r.type)model.filter=r.type;
    changeView('map');closeSheet();
    document.querySelector(`[data-reading="${r.id}"]`)?.focus({preventScroll:true});
  }
  if(action==='show-saved'){
    const r=model.rows.find(row=>row.id===button.dataset.id);
    if(model.filter!=='ALL'&&model.filter!==r.type)model.filter=r.type;
    changeView('readings');showDetails(r);
  }
});

$('scenario').addEventListener('change',event=>setScenario(event.target.value));
$('capture-type').addEventListener('change',()=>{render();announce(`Simulated active connection: ${typeName($('capture-type').value)}. Display filter unchanged.`);});
$('location-reason').addEventListener('change',()=>{
  if(model.scenario==='saved-no-pin')setScenario('saved-no-pin');
  else announce(`Missing-location example: ${reasons[$('location-reason').value]}`);
});
$('theme-toggle').addEventListener('click',()=>{
  const dark=document.documentElement.classList.toggle('dark');
  $('theme-toggle').setAttribute('aria-pressed',String(dark));
  $('theme-toggle').textContent=dark?'Light appearance':'Dark appearance';
});
$('reset').addEventListener('click',()=>{model.permissionExplained=false;$('capture-type').value='WIFI';$('location-reason').value='APPROXIMATE_ONLY';setScenario('ready');announce('Demo reset. Six illustrative readings.');});
$('sheet').addEventListener('cancel',event=>{
  if(model.busy){event.preventDefault();if(!$('sheet-content').querySelector('.saving')){clearTimeout(model.timer);model.busy=false;model.candidate=null;render();closeSheet();announce('Recording cancelled.');}}
});
window.addEventListener('resize',positionSheet);
window.addEventListener('scroll',positionSheet,{passive:true});
document.querySelectorAll('[data-icon]').forEach(el=>el.innerHTML=icon(el.dataset.icon));
render();
