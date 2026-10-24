const KEY = 'cowboy-web-data-v1';
const BACKUP_FORMAT = 'cowboy-backup';
const ANIMAL_DATA_FORMAT = 'cowboy-animal-data';
const DATA_SCHEMA_VERSION = 1;
const dateKey = date => { const d = date || new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; };
const prettyDate = key => new Date(`${key}T12:00:00`).toLocaleDateString(undefined,{weekday:'long',month:'long',day:'numeric',year:'numeric'});
const splitLines = value => value.split('\n').map(s=>s.trim()).filter(Boolean);
const DEFAULT_CONFIG = {
  sections:{East:['A','22','21','20','8','14','13','12','11','SP','9','E/D','B/C','10','3','4','5','6','7'],West:['1','2/15','17','31','32','33','34','35','36','37','38','40','43','44SP','WSP','45']},
  issues:{Respiratory:['Snotty nose','Head down','Looks off','Walks slow','Redish eyes'],Footrot:['Lame','Swollen foot','Footrot'],Buller:['Buller','Eye injury'],'Wrong Pen':[],Other:['Not eating','Weak','Looks off']},
  appearance:['Dull','Thin','Lame','Swollen','Wound','Rough coat','Eye discharge','Other'], colors:['Black','Red','Gray','Brown'], treatColors:['White','Green','Yellow','Red'],
  baseProducts:['Baytril','Loncor','Increxxa','LA200','Resflor','Excede'], secondaryProducts:['Tylan 200','Dexamethasone','Banamine','Vitamin C'],
  compatible:{Baytril:['Dexamethasone','Banamine'],Loncor:['Dexamethasone','Banamine','Vitamin C'],Increxxa:['Dexamethasone','Banamine'],LA200:['Tylan 200','Dexamethasone','Banamine'],Resflor:['Dexamethasone','Banamine'],Excede:['Dexamethasone','Banamine']},
  defaultSecondary:{Loncor:['Vitamin C']}, requiredSecondary:{LA200:['Tylan 200']}, destinations:['8 · Hospital','WSP · Hospital','SP · Sickpen','44SP · Buller / fat ration']
};
let state = loadState();
let deferredInstall = null;
let currentActionId = null;
let editingRecordId = null;
let toastTimer;
let historySelectedDate = null;
let historyMonthCursor = new Date(new Date().getFullYear(),new Date().getMonth(),1,12);
let historyInitialized = false;
function migrateConfig(config,version=0){if(version<2){config.secondaryProducts=[...new Set([...(config.secondaryProducts||[]),'Vitamin C'])];config.compatible={...(config.compatible||{}),Loncor:[...new Set([...(config.compatible?.Loncor||[]),'Vitamin C'])]};config.defaultSecondary={...(config.defaultSecondary||{}),Loncor:[...new Set([...(config.defaultSecondary?.Loncor||[]),'Vitamin C'])]};}return{config,configVersion:2};}
function loadState(){try{const saved=JSON.parse(localStorage.getItem(KEY)||'{}'),migrated=migrateConfig({...structuredClone(DEFAULT_CONFIG),...(saved.config||{})},saved.configVersion||0);return{...migrated,records:Array.isArray(saved.records)?saved.records:[]};}catch{return{...migrateConfig(structuredClone(DEFAULT_CONFIG)),records:[]};}}
function persist(){ localStorage.setItem(KEY,JSON.stringify(state)); render(); }
function esc(value=''){ return String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
function formatAnimal(record){ return [record.animalTag && `Tag ${record.animalTag}`,`Pen ${record.pen}`].filter(Boolean).join(' · '); }
function formatTemperature(value){const raw=String(value??'').trim();if(!raw)return'';if(raw.includes('.'))return raw.replace(/[^0-9.]/g,'');const digits=raw.replace(/\D/g,'');if(digits.length===2)return`10${digits[0]}.${digits[1]}`;if(digits.length===4)return`${digits.slice(0,3)}.${digits[3]}`;return digits;}
function badge(text, extra=''){return `<span class="status-badge ${extra}">${esc(text)}</span>`;}
function recordCard(record, watch=false){
  const symptomChips=(record.issues||[]).map(x=>`<span class="mini-chip">${esc(x)}</span>`).join('');
  const appearanceChips=(record.appearance||[]).map(x=>`<span class="mini-chip">${esc(x)}</span>`).join('');
  const healthFlags=record.diagnosis||symptomChips?`<section class="record-health-flags">${record.diagnosis?`<h3 class="record-diagnosis">Diagnosis · ${esc(record.diagnosis)}</h3>`:''}${symptomChips?`<div class="record-flag-group"><h4>Symptoms</h4><div class="mini-chips">${symptomChips}</div></div>`:''}</section>`:'';
  const scheduled=record.status==='watch'&&record.followUpDate>dateKey();
  const status=record.status==='treated'?'Treated':record.status==='pulled'?'Pulled':record.status==='moved'?'Moved to tomorrow':record.status==='watch'?(scheduled?'Due tomorrow':'Check today'):record.status==='removed'?'Removed':record.status==='fine'?'Fine':'To pull';
  const tag=record.treatTagNumber?`<span class="card-meta">Treat tag ${esc(record.treatTagColor||'')} ${esc(record.treatTagNumber)}</span>`:'';
  let actions='';
  if(watch) actions=`<button class="secondary-button" data-action="fine" data-id="${record.id}">Looks fine</button><button class="primary-button" data-action="pull" data-id="${record.id}">Pull today</button>`;
  else if(record.status==='open') actions=`<button class="secondary-button" data-action="watch" data-id="${record.id}">Check Again Tomorrow</button><button class="primary-button" data-action="pull" data-id="${record.id}">Mark pulled</button>`;
  else if(record.status==='pulled') actions=`<button class="primary-button" data-action="treat" data-id="${record.id}">Record treatment</button>`;
  else if(record.status==='watch'&&scheduled) actions='';
  else if(record.status==='watch') actions=`<button class="primary-button" data-action="pull" data-id="${record.id}">Pull today</button><button class="secondary-button" data-action="fine" data-id="${record.id}">Looks fine</button>`;
  else if(record.status==='fine') actions=`<button class="secondary-button" data-action="undo-fine" data-id="${record.id}">Undo marked fine</button>`;
  const linkedFrom=record.followUpFromId?state.records.find(r=>r.id===record.followUpFromId):null;
  const dated=watch?`<p class="watch-origin">Checked ${esc(prettyDate(record.checkedDate||record.date))} · due today</p>`:record.status==='moved'?`<p class="watch-origin">Checked today · moved to ${esc(prettyDate(record.followUpDate))}</p>`:linkedFrom?`<p class="watch-origin">Follow-up from ${esc(prettyDate(linkedFrom.date))}</p>`:scheduled?`<p class="watch-origin">Checked today · due ${esc(prettyDate(record.followUpDate))}</p>`:'';
  const treatmentDestination=record.treatment?.destination||record.destination||'';
  const expandedDetails=appearanceChips||record.notes||record.treatment||tag?`<div class="card-expanded-details" hidden>${appearanceChips?`<div class="record-flag-group"><h4>Animal Description</h4><div class="mini-chips">${appearanceChips}</div></div>`:''}${record.notes?`<p class="record-notes">${esc(record.notes)}</p>`:''}${record.treatment?`<section class="treatment-summary"><h3>Treatment</h3><p class="treatment-products">${esc([record.treatment.base,...(record.treatment.secondary||[])].filter(Boolean).join(' + '))}</p>${record.treatment.temperature?`<p class="treatment-fact"><strong>Temperature</strong><span>${esc(record.treatment.temperature)}°F</span></p>`:''}${treatmentDestination?`<p class="treatment-fact"><strong>Sent to</strong><span>${esc(treatmentDestination)}</span></p>`:''}</section>`:''}${tag}</div>`:'';
  return `<article class="animal-card ${record.status==='treated'?'is-treated':''}" role="group" tabindex="0" aria-expanded="false"><div class="card-top"><div><div class="animal-title">${esc(formatAnimal(record))}</div><div class="card-meta">${esc(record.color||'Color not recorded')} ${record.treatDate?`· Last treated ${esc(record.treatDate)}`:''}</div></div><div class="card-tools">${badge(status,record.status)}<button type="button" class="card-menu-button" data-record-menu="${record.id}" aria-label="Manage ${esc(formatAnimal(record))}" title="More actions">⋮</button></div></div>${dated}${healthFlags}${expandedDetails?`<div class="card-expand-hint"><span>More details</span><span aria-hidden="true">⌄</span></div>${expandedDetails}`:''}<div class="card-actions">${actions}</div></article>`;
}
function openRecordMenu(record){
  const dialog=document.querySelector('#recordMenuDialog'),items=document.querySelector('#recordMenuItems');items.innerHTML='';
  const actions=[['Edit animal details',()=>editAnimalRecord(record)]];
  if(record.status==='treated')actions.push(['Edit treatment',()=>openAction(record,'treatEdit')]);
  if(record.status==='pulled'||record.status==='treated')actions.push(['Undo pull',()=>undoPull(record)]);
  if(record.status==='moved'&&state.records.find(item=>item.id===record.followUpRecordId)?.followUpFromId===record.id)actions.push(['Undo check',()=>undoWatch(record)]);
  if(record.status==='fine')actions.push(['Undo marked fine',()=>undoFine(record)]);
  actions.push(['Remove entry',()=>removeEntry(record)]);
  for(const [label,action] of actions){const button=document.createElement('button');button.type='button';button.className=`record-menu-item${label==='Remove entry'?' destructive':''}`;button.textContent=label;button.onclick=()=>{dialog.close();action();};items.append(button);}
  dialog.showModal();
}
function removeEntry(record){
  if(!window.confirm(`Remove this entry for ${formatAnimal(record)}? This cannot be undone.`))return;
  state.records=state.records.filter(item=>item.id!==record.id);
  for(const item of state.records){if(item.followUpFromId===record.id)delete item.followUpFromId;if(item.followUpRecordId===record.id)delete item.followUpRecordId;}
  persist();toast('Entry removed');
}
function undoPull(record){record.status='open';record.treatment=null;record.destination='';record.pullNotes='';record.treatedDate='';persist();toast('Pull status undone');}
function undoWatch(record){
  const linked=state.records.find(item=>item.id===record.followUpRecordId);
  if(linked?.followUpFromId===record.id){state.records=state.records.filter(item=>item.id!==linked.id);record.status='open';delete record.checkedDate;delete record.followUpDate;delete record.followUpRecordId;}
  else {const active=state.records.find(item=>item.id===record.followUpRecordId);if(!active||active.followUpFromId!==record.id)return;active.date=record.date;active.status='open';delete active.followUpFromId;state.records=state.records.filter(item=>item.id!==record.id);}
  persist();toast('Tomorrow follow-up undone');
}
function undoFine(record){record.status=record.statusBeforeFine==='watch'?'watch':'open';delete record.statusBeforeFine;delete record.resolvedDate;persist();toast('Fine status undone');}
function renderHistory(){
  const records=state.records;
  if(!historyInitialized){const latest=records.map(r=>r.date).filter(Boolean).sort().at(-1);historySelectedDate=latest||dateKey();const chosen=new Date(`${historySelectedDate}T12:00:00`);historyMonthCursor=new Date(chosen.getFullYear(),chosen.getMonth(),1,12);historyInitialized=true;}
  const year=historyMonthCursor.getFullYear(),month=historyMonthCursor.getMonth(),days=new Date(year,month+1,0).getDate();
  document.querySelector('#historyMonthLabel').textContent=historyMonthCursor.toLocaleDateString(undefined,{month:'long',year:'numeric'});
  const grid=document.querySelector('#historyCalendar');grid.innerHTML='';
  ['Sun','Mon','Tue','Wed','Thu','Fri','Sat'].forEach((name,index)=>{const label=document.createElement('div');label.className=`history-weekday${index===0||index===6?' weekend':''}`;label.textContent=name;grid.append(label);});
  const firstWeekday=new Date(year,month,1).getDay();
  for(let blank=0;blank<firstWeekday;blank++){const spacer=document.createElement('div');spacer.className=`history-calendar-blank${blank===0||blank===6?' weekend':''}`;grid.append(spacer);}
  for(let day=1;day<=days;day++){
    const key=dateKey(new Date(year,month,day,12));
    const dayRecords=records.filter(record=>record.date===key);
    const pulls=dayRecords.filter(record=>record.status==='pulled'||record.status==='treated').length;
    const button=document.createElement('button');button.type='button';
    button.className=`history-day${historySelectedDate===key?' selected':''}${(firstWeekday+day-1)%7===0||(firstWeekday+day-1)%7===6?' weekend':''}`;
    button.innerHTML=`<span class="history-day-number">${day}</span><span class="history-pull-count">${dayRecords.length?`${pulls} ${pulls===1?'pull':'pulls'}`:'&nbsp;'}</span>`;
    button.setAttribute('aria-label',`${prettyDate(key)}, ${pulls} ${pulls===1?'pull':'pulls'}`);
    button.setAttribute('aria-pressed',historySelectedDate===key?'true':'false');
    button.onclick=()=>{historySelectedDate=key;renderHistory();};grid.append(button);
  }
  const selectedRecords=historySelectedDate?records.filter(record=>record.date===historySelectedDate):[];
  document.querySelector('#historySelectedLabel').textContent=historySelectedDate?prettyDate(historySelectedDate):'Choose a day with records';
  const query=(document.querySelector('#historySearch').value||'').toLowerCase();
  const visible=selectedRecords.filter(r=>`${r.pen} ${r.animalTag||''} ${r.treatTagNumber||''} ${r.notes||''} ${r.diagnosis||''} ${(r.issues||[]).join(' ')} ${(r.appearance||[]).join(' ')}`.toLowerCase().includes(query)).sort((a,b)=>b.createdAt-a.createdAt);
  document.querySelector('#historyList').innerHTML=visible.map(record=>recordCard(record)).join('');
  const empty=document.querySelector('#historyEmpty');empty.hidden=visible.length>0;
  empty.querySelector('strong').textContent=selectedRecords.length&&!visible.length?'No matching records':'No animals recorded';
  empty.querySelector('p').textContent=selectedRecords.length&&!visible.length?'Try another search term.':historySelectedDate?'No animals were entered for this date.':'Choose a date with records to view its animals.';
}
function shiftHistoryMonth(amount){historyMonthCursor=new Date(historyMonthCursor.getFullYear(),historyMonthCursor.getMonth()+amount,1,12);const prefix=`${historyMonthCursor.getFullYear()}-${String(historyMonthCursor.getMonth()+1).padStart(2,'0')}`;historySelectedDate=state.records.map(r=>r.date).filter(date=>date?.startsWith(prefix)).sort().at(-1)||null;renderHistory();}
function render(){
  const today=dateKey(); document.querySelector('#todayDate').textContent=new Date().toLocaleDateString(undefined,{weekday:'long',month:'long',day:'numeric'});
  const checkedTodayRecords=state.records.filter(r=>r.status==='moved'&&r.checkedDate===today);
  const todayRecords=state.records.filter(r=>r.date===today&&r.status!=='removed'&&r.status!=='fine'&&!checkedTodayRecords.includes(r));
  const watchRecords=state.records.filter(r=>r.status==='watch'&&r.followUpDate<=today);
  document.querySelector('#todayCount').textContent=`${todayRecords.length} ${todayRecords.length===1?'animal':'animals'}`;
  document.querySelector('#todayList').innerHTML=todayRecords.map(r=>recordCard(r)).join('');
  document.querySelector('#todayEmpty').hidden=todayRecords.length>0||checkedTodayRecords.length>0;
  document.querySelector('#checkedTodaySection').hidden=checkedTodayRecords.length===0;
  document.querySelector('#checkedTodayList').innerHTML=checkedTodayRecords.map(r=>recordCard(r)).join('');
  document.querySelector('#watchlistSection').hidden=watchRecords.length===0;
  document.querySelector('#watchlistList').innerHTML=watchRecords.map(r=>recordCard(r,true)).join('');
  renderHistory();
}
function selectPage(pageId){ document.querySelectorAll('.page').forEach(p=>p.classList.toggle('active',p.id===pageId));document.querySelectorAll('.nav-item').forEach(b=>b.classList.toggle('selected',b.dataset.page===pageId));if(pageId==='historyPage')render(); }
document.querySelectorAll('.nav-item').forEach(b=>b.addEventListener('click',()=>selectPage(b.dataset.page)));
document.querySelector('#historyShortcut').onclick=()=>selectPage('historyPage');
document.querySelector('#historySearch').addEventListener('input',render);
document.querySelector('#historyPrevMonth').onclick=()=>shiftHistoryMonth(-1);
document.querySelector('#historyNextMonth').onclick=()=>shiftHistoryMonth(1);
function chipPicker(node, groups, selected, multi=true, colorPalette=false){
  node.innerHTML='';
  for(const [title,values] of Object.entries(groups)){
    if(title) node.insertAdjacentHTML('beforeend',`<div class="chip-category">${esc(title)}</div>`);
    const wrap=document.createElement('div');wrap.className='chip-wrap';
    for(const value of values){const chip=document.createElement('button');chip.type='button';chip.className=`select-chip ${selected.includes(value)?'active':''}`;chip.textContent=value;if(colorPalette){chip.classList.add('color-chip');const palette={black:['#30343b','#fff'],red:['#ef493d','#fff'],gray:['#8d969f','#fff'],grey:['#8d969f','#fff'],brown:['#a65e32','#fff']};const [background,foreground]=palette[value.toLowerCase()]||['hsl('+[...value].reduce((n,c)=>(n*31+c.charCodeAt(0))%360,0)+' 60% 52%)','#fff'];chip.style.backgroundColor=background;chip.style.color=foreground;}chip.onclick=()=>{if(multi){selected.includes(value)?selected.splice(selected.indexOf(value),1):selected.push(value);chip.classList.toggle('active');}else{selected.splice(0,selected.length,value);wrap.querySelectorAll('.select-chip').forEach(c=>c.classList.remove('active'));chip.classList.add('active');}};wrap.append(chip);} node.append(wrap);
  }
}
function showChoice(title, items, onChoose, sections=false){
  const dialog=document.querySelector('#choiceDialog'), search=document.querySelector('#choiceSearch'), content=document.querySelector('#choiceContent');
  document.querySelector('#choiceTitle').textContent=title;document.querySelector('#choiceBack').hidden=true;search.hidden=false;search.value='';
  const paint=()=>{const q=search.value.toLowerCase();content.innerHTML='';for(const [group,values] of Object.entries(items)){const filtered=values.filter(v=>v.toLowerCase().includes(q));if(!filtered.length)continue;if(sections)content.insertAdjacentHTML('beforeend',`<div class="choice-section-title">${esc(group)}</div>`);const grid=document.createElement('div');grid.className=sections?'pen-grid':'choice-list';filtered.forEach(value=>{const button=document.createElement('button');button.type='button';button.className=sections?'pen-option':'choice-option';button.textContent=value;button.onclick=()=>{dialog.close();onChoose(value,group);};grid.append(button);});content.append(grid);}};
  search.oninput=paint;paint();dialog.showModal();
}
const treatDateMonths=['January','February','March','April','May','June','July','August','September','October','November','December'];
function showTreatDatePicker(){
  const dialog=document.querySelector('#choiceDialog'),content=document.querySelector('#choiceContent'),search=document.querySelector('#choiceSearch'),back=document.querySelector('#choiceBack');
  search.hidden=true;back.hidden=true;
  const today=new Date();today.setHours(12,0,0,0);
  const earliest=new Date(today);earliest.setDate(earliest.getDate()-299);
  const paintMonths=()=>{
    back.hidden=true;document.querySelector('#choiceTitle').textContent='Choose month';
    content.innerHTML='<div class="month-grid"></div>';const grid=content.firstElementChild;
    const cursor=new Date(earliest.getFullYear(),earliest.getMonth(),1,12);
    const lastMonth=new Date(today.getFullYear(),today.getMonth(),1,12);
    while(cursor<=lastMonth){
      const month=cursor.getMonth(),year=cursor.getFullYear(),button=document.createElement('button');
      button.type='button';button.className='month-bubble';
      button.innerHTML=`<span>${treatDateMonths[month]}</span><small>${year}</small>`;
      button.onclick=()=>paintCalendar(month,year);grid.append(button);
      cursor.setMonth(cursor.getMonth()+1);
    }
  };
  const paintCalendar=(month,year)=>{
    back.hidden=false;document.querySelector('#choiceTitle').textContent=`${treatDateMonths[month]} ${year}`;
    content.innerHTML='<div class="calendar-grid"></div>';const grid=content.firstElementChild;
    ['Sun','Mon','Tue','Wed','Thu','Fri','Sat'].forEach((day,index)=>{const label=document.createElement('div');label.className=`calendar-weekday${index===0||index===6?' weekend':''}`;label.textContent=day;grid.append(label);});
    const offset=new Date(year,month,1).getDay(),days=new Date(year,month+1,0).getDate();
    for(let blank=0;blank<offset;blank++){const spacer=document.createElement('div');spacer.className=`calendar-blank${blank===0||blank===6?' weekend':''}`;grid.append(spacer);}
    for(let day=1;day<=days;day++){
      const date=new Date(year,month,day,12),button=document.createElement('button');
      button.type='button';button.className=`calendar-day${(offset+day-1)%7===0||(offset+day-1)%7===6?' weekend':''}`;button.textContent=day;
      button.disabled=date>today||date<earliest;
      button.setAttribute('aria-label',date.toLocaleDateString(undefined,{weekday:'long',month:'long',day:'numeric',year:'numeric'}));
      button.onclick=()=>{const value=`${month+1}/${day}`;document.querySelector('#treatDateValue').textContent=`Last treated ${value}`;document.querySelector('#treatDateValue').dataset.value=value;dialog.close();};
      grid.append(button);
    }
  };
  back.onclick=paintMonths;paintMonths();dialog.showModal();
}
const diagnosisSelection=[],issueSelection=[],appearanceSelection=[],colorSelection=[];
function updateRequiredFields(){const hasPen=Boolean(document.querySelector('#penInput').value),hasDiagnosis=Boolean(diagnosisSelection[0]),hasColor=Boolean(colorSelection[0]);document.querySelector('#diagnosisRequiredMark').hidden=hasDiagnosis;document.querySelector('#colorRequiredMark').hidden=hasColor;document.querySelector('#saveAnimalButton').disabled=!(hasPen&&hasDiagnosis&&hasColor);}
function renderColorStep(){const color=colorSelection[0];document.querySelector('#colorHeading').textContent=color?`Color: ${color}`:'Animal color';document.querySelector('#colorRequiredMark').hidden=Boolean(color);document.querySelector('#colorChips').hidden=Boolean(color);document.querySelector('#changeColor').hidden=!color;chipPicker(document.querySelector('#colorChips'),{'':state.config.colors},colorSelection,false,true);document.querySelectorAll('#colorChips .select-chip').forEach(chip=>chip.addEventListener('click',renderColorStep));updateRequiredFields();}
function renderSymptoms(){const diagnosis=diagnosisSelection[0];const symptoms=diagnosis?(state.config.issues[diagnosis]||[]):[];document.querySelector('#postDiagnosisFields').hidden=!diagnosis;document.querySelector('#diagnosisChips').hidden=Boolean(diagnosis);document.querySelector('#diagnosisHeading').textContent=diagnosis?`Diagnosis: ${diagnosis}`:'Diagnose';document.querySelector('#diagnosisRequiredMark').hidden=Boolean(diagnosis);document.querySelector('#changeDiagnosis').hidden=!diagnosis;document.querySelector('#noSymptomsMessage').hidden=!diagnosis||symptoms.length>0;chipPicker(document.querySelector('#issueChips'),diagnosis?{'':symptoms}:{},issueSelection);renderColorStep();}
const treatTagPalette={none:{background:'#d9ded9',foreground:'#303831'},white:{background:'#fff',foreground:'#263126'},green:{background:'#35a853',foreground:'#fff'},yellow:{background:'#ffdc3d',foreground:'#332c15'},red:{background:'#e84b40',foreground:'#fff'}};
function updateTreatTagFields(){const select=document.querySelector('#treatTagColor');const details=document.querySelector('#treatTagDetails');const chosen=select.value;details.hidden=!chosen;const colors=treatTagPalette[chosen.toLowerCase()]||treatTagPalette.none;select.style.backgroundColor=colors.background;select.style.color=colors.foreground;if(!chosen){document.querySelector('#treatTagNumber').value='';document.querySelector('#treatDateValue').textContent='';document.querySelector('#treatDateValue').dataset.value='';}}
function updateFormChoices(){const diagnoses=Object.keys(state.config.issues);chipPicker(document.querySelector('#diagnosisChips'),{'':diagnoses},diagnosisSelection,false);document.querySelectorAll('#diagnosisChips .select-chip').forEach(chip=>chip.addEventListener('click',()=>{diagnosisSelection.splice(0,diagnosisSelection.length,chip.textContent);issueSelection.length=0;renderSymptoms();}));renderSymptoms();chipPicker(document.querySelector('#appearanceChips'),{'':state.config.appearance},appearanceSelection);const sel=document.querySelector('#treatTagColor');const noTag=new Option('No Tag','',true,true);noTag.style.backgroundColor=treatTagPalette.none.background;noTag.style.color=treatTagPalette.none.foreground;sel.replaceChildren(noTag);for(const color of state.config.treatColors){const option=new Option(color,color);const style=treatTagPalette[color.toLowerCase()]||{background:color,foreground:'#20251f'};option.style.backgroundColor=style.background;option.style.color=style.foreground;sel.add(option);}updateTreatTagFields();}
document.querySelector('#treatTagColor').addEventListener('change',updateTreatTagFields);
document.querySelector('#changeDiagnosis').onclick=()=>{diagnosisSelection.length=0;issueSelection.length=0;renderSymptoms();};
document.querySelector('#changeColor').onclick=()=>{document.querySelector('#colorChips').hidden=false;document.querySelector('#colorHeading').textContent='Animal color';document.querySelector('#changeColor').hidden=true;};
document.querySelector('#newAnimalButton').onclick=()=>{document.querySelector('#animalForm').reset();diagnosisSelection.length=issueSelection.length=appearanceSelection.length=colorSelection.length=0;document.querySelector('#penInput').value='';document.querySelector('#penInput').dataset.section='';document.querySelector('#penHeading').textContent='Pen';document.querySelector('#changePen').hidden=true;document.querySelector('#treatDateValue').textContent='';document.querySelector('#treatDateValue').dataset.value='';updateFormChoices();openPenPicker(()=>document.querySelector('#formDialog').showModal());};
const penInput=document.querySelector('#penInput');
const openPenPicker=onChoose=>showChoice('Select a section and pen',state.config.sections,(pen,section)=>{penInput.value=pen;penInput.dataset.section=section;document.querySelector('#penHeading').textContent=`Pen: ${pen}`;document.querySelector('#changePen').hidden=false;updateRequiredFields();onChoose?.();},true);
document.querySelector('#changePen').onclick=()=>openPenPicker();
document.querySelector('#treatDateButton').onclick=showTreatDatePicker;
function editAnimalRecord(record){
  editingRecordId=record.id;document.querySelector('#animalForm').reset();
  diagnosisSelection.length=issueSelection.length=appearanceSelection.length=colorSelection.length=0;
  const diagnosis=record.diagnosis||Object.entries(state.config.issues).find(([,values])=>(record.issues||[]).some(issue=>values.includes(issue)))?.[0]||Object.keys(state.config.issues)[0]||'';
  if(diagnosis)diagnosisSelection.push(diagnosis);issueSelection.push(...(record.issues||[]));appearanceSelection.push(...(record.appearance||[]));if(record.color)colorSelection.push(record.color);
  document.querySelector('#penInput').value=record.pen||'';document.querySelector('#penInput').dataset.section=record.section||'';document.querySelector('#penHeading').textContent=`Pen: ${record.pen||''}`;document.querySelector('#changePen').hidden=false;
  document.querySelector('#animalTag').value=record.animalTag||'';document.querySelector('#intakeNotes').value=record.notes||'';document.querySelector('#treatTagNumber').value=record.treatTagNumber||'';
  document.querySelector('#treatDateValue').textContent=record.treatDate?`Last treated ${record.treatDate}`:'';document.querySelector('#treatDateValue').dataset.value=record.treatDate||'';
  updateFormChoices();document.querySelector('#treatTagColor').value=record.treatTagColor||'';updateTreatTagFields();
  document.querySelector('#animalFormTitle').textContent='Edit animal';document.querySelector('#saveAnimalButton').textContent='Save changes';updateRequiredFields();document.querySelector('#formDialog').showModal();
}
document.querySelector('#formDialog').addEventListener('close',()=>{editingRecordId=null;document.querySelector('#animalFormTitle').textContent='Enter sick animal';document.querySelector('#saveAnimalButton').textContent='Save animal';});
document.querySelector('#animalForm').addEventListener('submit',e=>{
  if(e.submitter?.value==='cancel')return;e.preventDefault();
  const pen=document.querySelector('#penInput').value;if(!pen){toast('Choose a pen first');return;}
  if(!diagnosisSelection[0]){toast('Choose a diagnosis');return;}
  const color=colorSelection[0];if(!color){toast('Choose the animal color');return;}
  const fields={pen,section:document.querySelector('#penInput').dataset.section||'',animalTag:document.querySelector('#animalTag').value.trim(),diagnosis:diagnosisSelection[0],issues:[...issueSelection],appearance:[...appearanceSelection],color,treatTagColor:document.querySelector('#treatTagColor').value,treatTagNumber:document.querySelector('#treatTagNumber').value.trim(),treatDate:document.querySelector('#treatDateValue').dataset.value||'',notes:document.querySelector('#intakeNotes').value.trim()};
  const existing=editingRecordId&&state.records.find(record=>record.id===editingRecordId);
  if(existing)Object.assign(existing,fields);else state.records.push({id:crypto.randomUUID(),date:dateKey(),createdAt:Date.now(),...fields,status:'open'});
  document.querySelector('#formDialog').close();persist();toast(existing?'Animal changes saved':'Animal added to today');
});
document.body.addEventListener('click',e=>{
  const card=e.target.closest('.animal-card');
  if(card&&!e.target.closest('button,a,input,textarea,select')){toggleCardDetails(card);return;}
  const menuButton=e.target.closest('[data-record-menu]');
  if(menuButton){const record=state.records.find(r=>r.id===menuButton.dataset.recordMenu);if(record)openRecordMenu(record);return;}
  const b=e.target.closest('[data-action]');if(!b)return;const record=state.records.find(r=>r.id===b.dataset.id);if(!record)return;
  switch(b.dataset.action){case'watch':{const checkedDate=record.date||dateKey(),followUpDate=addDays(checkedDate,1),checkedCopy={...structuredClone(record),id:crypto.randomUUID(),date:checkedDate,createdAt:Date.now(),status:'moved',checkedDate,followUpDate,followUpRecordId:record.id};delete checkedCopy.followUpFromId;record.date=followUpDate;record.status='open';record.followUpFromId=checkedCopy.id;delete record.checkedDate;delete record.followUpDate;delete record.followUpRecordId;state.records.push(checkedCopy);persist();toast('Checked today · entry moved to tomorrow');break;}case'undo-watch':undoWatch(record);break;case'fine':record.statusBeforeFine=record.status;record.status='fine';record.resolvedDate=dateKey();persist();toast('Marked as fine');break;case'undo-fine':undoFine(record);break;case'pull':record.status='pulled';persist();toast('Marked pulled');break;case'treat':openAction(record,'treat');break;case'undo':undoPull(record);break;}
});
function toggleCardDetails(card){const details=card.querySelector('.card-expanded-details');if(!details)return;const expanded=card.classList.toggle('expanded');details.hidden=!expanded;card.setAttribute('aria-expanded',String(expanded));const hint=card.querySelector('.card-expand-hint');if(hint){hint.firstElementChild.textContent=expanded?'Hide details':'More details';hint.lastElementChild.textContent=expanded?'⌃':'⌄';}}
document.body.addEventListener('keydown',e=>{if((e.key==='Enter'||e.key===' ')&&e.target.matches('.animal-card')){e.preventDefault();toggleCardDetails(e.target);}});
function addDays(key,n){const d=new Date(`${key}T12:00:00`);d.setDate(d.getDate()+n);return dateKey(d);}
function openAction(record,kind){
  currentActionId=record.id;const content=document.querySelector('#actionContent');content.innerHTML='';const title=document.querySelector('#actionTitle');
  document.querySelector('#actionEyebrow').textContent=`${esc(formatAnimal(record))}`;
  {
    const editing=kind==='treatEdit',savedTreatment=editing?record.treatment:null;
    title.textContent=editing?'Edit treatment':'Record treatment';
    const baseWrap=document.createElement('div');
    const home=`Home · Pen ${record.pen}`;
    const destinations=[home,...(state.config.destinations||['8 · Hospital','WSP · Hospital','SP · Sickpen','44SP · Buller / fat ration'])];
    const savedDestination=savedTreatment?.destination||record.destination||home;
    if(savedDestination&&!destinations.includes(savedDestination))destinations.push(savedDestination);
    baseWrap.innerHTML='<div class="field-label">Send to</div><div id="destinationChips" class="chip-area"></div><div class="field-label">Choose one base product</div><div id="baseChips" class="chip-area"></div><section id="secondaryProductSection" hidden><div class="field-label">Additional products</div><div id="secondaryChips" class="chip-area"></div></section><label class="field-label" for="treatmentTemperature">Temperature (°F)</label><input id="treatmentTemperature" type="text" inputmode="decimal" autocomplete="off"><label class="field-label" for="treatmentNotes">Notes</label><textarea id="treatmentNotes" rows="3"></textarea>';
    content.append(baseWrap);
    chipPicker(document.querySelector('#destinationChips'),{'':destinations},[savedDestination],false);
    const baseSelection=savedTreatment?.base?[savedTreatment.base]:[],baseProducts=[...state.config.baseProducts];
    if(savedTreatment?.base&&!baseProducts.includes(savedTreatment.base))baseProducts.push(savedTreatment.base);
    chipPicker(document.querySelector('#baseChips'),{'':baseProducts},baseSelection,false);
    const renderSecondary=(base,selected)=>{const allowed=state.config.compatible[base]||[],required=(state.config.requiredSecondary||{})[base]||[];const values=[...new Set([...allowed,...required])];const initial=selected||(state.config.defaultSecondary||{})[base]||[];chipPicker(document.querySelector('#secondaryChips'),{'':values},[...initial,...required]);document.querySelectorAll('#secondaryChips .select-chip').forEach(chip=>{if(required.includes(chip.textContent)){chip.classList.add('active','locked');chip.disabled=true;}});document.querySelector('#secondaryProductSection').hidden=false;};
    document.querySelector('#baseChips').addEventListener('click',()=>{const base=document.querySelector('#baseChips .select-chip.active')?.textContent||'';if(base)renderSecondary(base);});
    if(savedTreatment?.base)renderSecondary(savedTreatment.base,savedTreatment.secondary||[]);
    const temperatureInput=document.querySelector('#treatmentTemperature');temperatureInput.value=savedTreatment?.temperature||'';temperatureInput.addEventListener('blur',()=>{temperatureInput.value=formatTemperature(temperatureInput.value);});
    document.querySelector('#treatmentNotes').value=savedTreatment?.notes||'';
    document.querySelector('#actionSaveButton').textContent=editing?'Save treatment changes':'Save treatment';
  }
  document.querySelector('#actionForm').dataset.kind=kind;document.querySelector('#actionDialog').showModal();
}
document.querySelector('#actionForm').addEventListener('submit',e=>{
  if(e.submitter?.value==='cancel')return;e.preventDefault();const record=state.records.find(r=>r.id===currentActionId);if(!record)return;
  const kind=e.currentTarget.dataset.kind;
  {
    const destination=document.querySelector('#destinationChips .select-chip.active')?.textContent||'';
    record.destination=destination;
    const base=document.querySelector('#baseChips .select-chip.active')?.textContent;if(!base){toast('Choose a base product');return;}
    const secondary=[...document.querySelectorAll('#secondaryChips .select-chip.active')].map(chip=>chip.textContent);
    const temperature=formatTemperature(document.querySelector('#treatmentTemperature').value);
    record.status='treated';
    record.treatment={base,secondary,temperature,notes:document.querySelector('#treatmentNotes').value.trim(),destination};
    record.treatedDate=dateKey();
  }
  document.querySelector('#actionDialog').close();persist();toast(kind==='treatEdit'?'Treatment updated':'Treatment saved');
});
function setConfigFields(){const c=state.config;document.querySelector('#configPens').value=Object.entries(c.sections).map(([s,p])=>`${s}: ${p.join(', ')}`).join('\n');document.querySelector('#configIssues').value=Object.entries(c.issues).map(([s,p])=>`${s}: ${p.join(', ')}`).join('\n');document.querySelector('#configAppearance').value=c.appearance.join('\n');document.querySelector('#configColors').value=c.colors.join('\n');document.querySelector('#configTreatColors').value=c.treatColors.join('\n');document.querySelector('#configBaseProducts').value=c.baseProducts.join('\n');document.querySelector('#configSecondaryProducts').value=c.secondaryProducts.join('\n');document.querySelector('#configCompatibility').value=Object.entries(c.compatible).map(([s,p])=>`${s}: ${p.join(', ')}`).join('\n');document.querySelector('#configDefaultSecondary').value=Object.entries(c.defaultSecondary||{}).map(([s,p])=>`${s}: ${p.join(', ')}`).join('\n');document.querySelector('#configRequiredSecondary').value=Object.entries(c.requiredSecondary||{}).map(([s,p])=>`${s}: ${p.join(', ')}`).join('\n');document.querySelector('#configDestinations').value=(c.destinations||[]).join('\n');}
function parseSections(text){const result={};for(const line of splitLines(text)){const at=line.indexOf(':');if(at<1)continue;result[line.slice(0,at).trim()]=line.slice(at+1).split(',').map(x=>x.trim()).filter(Boolean);}return result;}
document.querySelector('#editConfigButton').onclick=()=>{setConfigFields();document.querySelector('#configDialog').showModal();};
document.querySelector('#configForm').addEventListener('submit',e=>{if(e.submitter?.value==='cancel')return;e.preventDefault();const c=state.config;c.sections=parseSections(document.querySelector('#configPens').value);c.issues=parseSections(document.querySelector('#configIssues').value);c.appearance=splitLines(document.querySelector('#configAppearance').value);c.colors=splitLines(document.querySelector('#configColors').value);c.treatColors=splitLines(document.querySelector('#configTreatColors').value);c.baseProducts=splitLines(document.querySelector('#configBaseProducts').value);c.secondaryProducts=splitLines(document.querySelector('#configSecondaryProducts').value);c.compatible=parseSections(document.querySelector('#configCompatibility').value);c.defaultSecondary=parseSections(document.querySelector('#configDefaultSecondary').value);c.requiredSecondary=parseSections(document.querySelector('#configRequiredSecondary').value);c.destinations=splitLines(document.querySelector('#configDestinations').value);document.querySelector('#configDialog').close();persist();toast('Configuration saved');});
function downloadJson(payload,filename){const contents=JSON.stringify(payload,null,2);if(window.CowboyNative?.exportJson){window.CowboyNative.exportJson(filename,contents);return;}const blob=new Blob([contents],{type:'application/json'}),url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download=filename;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
function validateSchema(payload,expectedFormat){if(!payload||typeof payload!=='object'||Array.isArray(payload))throw new Error('The selected file is not a Cowboy data file.');const version=payload.schemaVersion??0;if(!Number.isInteger(version)||version<0)throw new Error('The file has an invalid schema version.');if(version>DATA_SCHEMA_VERSION)throw new Error(`This file uses Cowboy data format version ${version}, newer than this app supports (${DATA_SCHEMA_VERSION}). Update Cowboy before importing it.`);if(payload.format&&payload.format!==expectedFormat)throw new Error(`This is a ${payload.format} file, not a ${expectedFormat} file.`);return version;}
document.querySelector('#exportButton').onclick=()=>downloadJson({format:BACKUP_FORMAT,schemaVersion:DATA_SCHEMA_VERSION,exportedAt:new Date().toISOString(),configVersion:state.configVersion||2,config:state.config,records:state.records},`cowboy-backup-v${DATA_SCHEMA_VERSION}-${dateKey()}.json`);
document.querySelector('#importButton').onclick=()=>document.querySelector('#importFile').click();
document.querySelector('#importFile').addEventListener('change',async e=>{const file=e.target.files?.[0];if(!file)return;try{const imported=JSON.parse(await file.text());validateSchema(imported,BACKUP_FORMAT);if(!Array.isArray(imported.records)||!imported.config||typeof imported.config!=='object'||Array.isArray(imported.config))throw new Error('This file does not look like a Cowboy backup.');if(!confirm(`Replace this device's ${state.records.length} records and settings with the ${imported.records.length} records and settings from this backup?`))return;const migrated=migrateConfig({...structuredClone(DEFAULT_CONFIG),...imported.config},imported.configVersion||0);state={...migrated,records:imported.records};persist();toast('Backup restored');}catch(error){showMessage('Could not import backup',error.message||'Choose a valid Cowboy JSON backup.');}finally{e.target.value='';}});
document.querySelector('#exportAnimalsButton').onclick=()=>downloadJson({format:ANIMAL_DATA_FORMAT,schemaVersion:DATA_SCHEMA_VERSION,exportedAt:new Date().toISOString(),records:state.records},`cowboy-animals-v${DATA_SCHEMA_VERSION}-${dateKey()}.json`);
document.querySelector('#importAnimalsButton').onclick=()=>document.querySelector('#importAnimalsFile').click();
document.querySelector('#importAnimalsFile').addEventListener('change',async e=>{const file=e.target.files?.[0];if(!file)return;try{const imported=JSON.parse(await file.text());if(Array.isArray(imported)){if(imported.some(record=>!record||typeof record!=='object'||Array.isArray(record)))throw new Error('The animal data file contains an invalid record.');}else{validateSchema(imported,ANIMAL_DATA_FORMAT);if(!Array.isArray(imported.records)||imported.records.some(record=>!record||typeof record!=='object'||Array.isArray(record)))throw new Error('This file does not contain valid animal data.');}const records=Array.isArray(imported)?imported:imported.records;if(!confirm(`Import ${records.length} animal records into this device? Existing records and settings will be kept; matching record IDs will be skipped.`))return;const existingIds=new Set(state.records.map(record=>record.id).filter(Boolean)),newRecords=records.filter(record=>!record.id||!existingIds.has(record.id)).map(record=>({...record,id:record.id||crypto.randomUUID()})),skipped=records.length-newRecords.length;state.records.push(...newRecords);persist();toast(`Imported ${newRecords.length} animals${skipped?`; skipped ${skipped} duplicates`:''}`);}catch(error){showMessage('Could not import animal data',error.message||'Choose a valid Cowboy animal data JSON file.');}finally{e.target.value='';}});
function showMessage(title,message){document.querySelector('#messageTitle').textContent=title;document.querySelector('#messageContent').textContent=message;document.querySelector('#messageDialog').showModal();}
if(window.CowboyNative){document.querySelector('#androidTools').hidden=false;document.querySelector('#androidVersion').textContent=`Version ${window.CowboyNative.versionName()}`;document.querySelector('#checkAndroidUpdate').onclick=()=>window.CowboyNative.checkForUpdate();document.querySelector('#shareCrashReport').onclick=()=>window.CowboyNative.shareCrashReport();}
function toast(message){const node=document.querySelector('#toast');node.textContent=message;node.classList.add('show');clearTimeout(toastTimer);toastTimer=setTimeout(()=>node.classList.remove('show'),2600);}
window.addEventListener('beforeinstallprompt',event=>{event.preventDefault();deferredInstall=event;const button=document.querySelector('#installButton');button.hidden=false;button.onclick=async()=>{deferredInstall.prompt();await deferredInstall.userChoice;deferredInstall=null;button.hidden=true;};});
window.addEventListener('appinstalled',()=>toast('Cowboy installed'));
if('serviceWorker' in navigator && location.protocol!=='file:')navigator.serviceWorker.register('./sw.js').catch(()=>{});
render();
