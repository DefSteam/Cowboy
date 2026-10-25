(() => {
  const translations = {
    es: {
      'Today':'Hoy','Home':'Inicio','History':'Historial','Settings':'Configuración','Offline':'Sin conexión','Offline · save problem':'Sin conexión · error al guardar',
      'To pull':'Por retirar','Pulled':'Retirados','Treated':'Tratados','Recheck':'Revisar','Enter sick animal':'Registrar animal enfermo','Checked today · due tomorrow':'Revisados hoy · revisar mañana','Check again today':'Revisar hoy','Today\'s animals':'Animales de hoy','All clear for today':'Todo en orden hoy','Add an animal when you find one that needs attention.':'Agregue un animal cuando encuentre uno que necesite atención.','Hide details':'Ocultar detalles','More details':'Más detalles','Color not recorded':'Color no registrado','Diagnosis':'Diagnóstico','Symptoms':'Síntomas','Treatment':'Tratamiento','Temperature':'Temperatura','Sent to':'Enviar a','Edit animal details':'Editar datos del animal','An animal':'Un animal',
      'Select a day':'Seleccione un día','Choose a day with records':'Elija un día con registros','Search tag, pen, or notes':'Buscar arete, corral o notas','No matching records':'No hay registros que coincidan','Daily records will show here after you add animals.':'Los registros diarios aparecerán aquí después de agregar animales.','No animals recorded':'No hay animales registrados','No animals were entered for this date.':'No se registraron animales en esta fecha.','Try another search term.':'Pruebe con otro término de búsqueda.',
      'Feedlot setup':'Configuración del corral','Advanced settings':'Configuración avanzada','Backups, data transfer, and diagnostic tools.':'Copias de seguridad, transferencia de datos y herramientas de diagnóstico.','Open advanced settings':'Abrir configuración avanzada','Cowboy updates':'Actualizaciones de Cowboy','App':'Aplicación','Runtime':'Sistema','Restart App':'Reiniciar aplicación','Check for updates':'Buscar actualizaciones',
      'Data and tools':'Datos y herramientas','Feedlot configuration':'Configuración del corral','Set up pens, diagnoses, flags, colors, and treatment products.':'Configure corrales, diagnósticos, síntomas, colores y tratamientos.','Edit setup':'Editar configuración','Install runtime bundle':'Instalar paquete del sistema','Choose a versioned .cowboy-runtime release package saved on this device. Cowboy checks its signature and contents before staging it for the next launch.':'Elija un paquete de versión .cowboy-runtime guardado en este dispositivo. Cowboy verificará su firma y contenido antes de prepararlo para el próximo inicio.','Choose runtime bundle':'Elegir paquete del sistema','Feedlot setup backup':'Respaldo de configuración','Move pen, diagnosis, and product configuration between devices.':'Transfiera la configuración de corrales, diagnósticos y productos entre dispositivos.','Import setup':'Importar configuración','Export setup':'Exportar configuración','Full backup':'Respaldo completo','Includes all animal records and feedlot setup. Import replaces the current data on this device.':'Incluye todos los registros de animales y la configuración del corral. La importación reemplaza los datos actuales de este dispositivo.','Import full backup':'Importar respaldo completo','Export full backup':'Exportar respaldo completo','Animal records':'Registros de animales','Import adds records and skips matching IDs. It keeps existing records and does not change feedlot setup.':'La importación agrega registros y omite los ID duplicados. Conserva los registros existentes y no cambia la configuración.','Import records':'Importar registros','Export records':'Exportar registros','Crash report':'Informe de error','Share the latest debug crash report to help diagnose a problem.':'Comparta el informe de error más reciente para ayudar a diagnosticar un problema.','Share crash report':'Compartir informe de error',
      'Daily record':'Registro diario','Pen':'Corral','Change':'Cambiar','Animal tag':'Arete del animal','optional':'opcional','Diagnose':'Diagnóstico','Animal color':'Color del animal','No symptom flags are set for this diagnosis. You can add them in Settings → Edit lists.':'Este diagnóstico no tiene síntomas configurados. Puede agregarlos en Configuración → Editar listas.','Animal Description':'Descripción del animal','Treat tag':'Arete de tratamiento','No Tag':'Sin arete','Tag number':'Número de arete','＋ Add last treat date':'＋ Agregar fecha del último tratamiento','Notes':'Notas','Anything else to remember':'Cualquier otro detalle','Cancel':'Cancelar','Save animal':'Guardar animal','Save changes':'Guardar cambios','Choose':'Elegir','Filter':'Filtrar','Manage animal':'Administrar animal','Update record':'Actualizar registro','Mark pulled':'Marcar retirado','Save':'Guardar','Edit configuration':'Editar configuración','Preview':'Vista previa','Customizable for your feedlot':'Personalizable para el corral','How this will look':'Vista previa','Done':'Listo','Edit option':'Editar opción','Name':'Nombre','Confirm action':'Confirmar acción','Remove entry':'Eliminar registro','Update available':'Actualización disponible','A newer version of Cowboy is ready to download.':'Hay una nueva versión de Cowboy lista para descargar.','Not now':'Ahora no','Download update':'Descargar actualización','Update ready':'Actualización lista','The update is downloaded and ready to install.':'La actualización se descargó y está lista para instalar.','Later':'Más tarde','Install now':'Instalar ahora',
      'Looks fine':'Se ve bien','Pull today':'Retirar hoy','Check Again Tomorrow':'Revisar mañana','Record treatment':'Registrar tratamiento','Undo marked fine':'Deshacer “se ve bien”','Edit treatment':'Editar tratamiento','Undo pull':'Deshacer retiro','Undo check':'Deshacer revisión','To pull':'Por retirar','Moved to tomorrow':'Pasado a mañana','Check today':'Revisar hoy','Removed':'Eliminado','Fine':'Se ve bien','Treated':'Tratado','Pulled':'Retirado',
      'Select a pen':'Seleccione un corral','Choose a Pen':'Elegir corral','Previous month':'Mes anterior','Next month':'Mes siguiente','Language':'Idioma','Choose the language used in Cowboy.':'Elija el idioma de Cowboy.','English':'Inglés','Spanish':'Español','Send to':'Enviar a','Choose one base product':'Elija un producto base','Additional products':'Productos adicionales','Temperature (°F)':'Temperatura (°F)','Treatment notes':'Notas del tratamiento','Choose a base product':'Elija un producto base','Base product':'Producto base','No options yet':'Aún no hay opciones','Delete':'Eliminar','Edit':'Editar','Add':'Agregar','Add a diagnosis category':'Agregar una categoría de diagnóstico','Pens':'Corrales','Animal':'Animal','Products':'Productos','Destinations':'Destinos','Diagnoses and symptoms':'Diagnósticos y síntomas','Create diagnosis categories and the symptom flags shown for each one.':'Cree categorías de diagnóstico y los síntomas que aparecerán en cada una.','Animal description flags':'Síntomas de descripción del animal','Choose the quick-select flags shown under “Animal Description”.':'Elija los indicadores rápidos de “Descripción del animal”.','Animal colors':'Colores de animales','Choose a color for the required animal color step.':'Elija un color para el paso obligatorio de color del animal.','Treat tag colors':'Colores de aretes de tratamiento','Choose the colors available for treat tags.':'Elija los colores disponibles para los aretes de tratamiento.','Only one base product can be selected for a treatment.':'Solo se puede elegir un producto base por tratamiento.','These can be enabled per base product below.':'Puede habilitarlos para cada producto base.','Pull destinations':'Destinos después del retiro','Hospital pens and other destination choices.':'Corrales de hospital y otros destinos.','Save configuration':'Guardar configuración','Preview':'Vista previa','How this will look':'Así se verá','Pens selection':'Selección de corrales','Animal entry':'Registro de animal','Treatment products':'Productos de tratamiento','Pull destinations':'Destinos de retiro','Month':'Mes','Sun':'Dom','Mon':'Lun','Tue':'Mar','Wed':'Mié','Thu':'Jue','Fri':'Vie','Sat':'Sáb','January':'Enero','February':'Febrero','March':'Marzo','April':'Abril','May':'Mayo','June':'Junio','July':'Julio','August':'Agosto','September':'Septiembre','October':'Octubre','November':'Noviembre','December':'Diciembre',
      'Could not save data. Check device storage.':'No se pudieron guardar los datos. Revise el almacenamiento.','Entry removed':'Registro eliminado','Pull status undone':'Se deshizo el retiro','Tomorrow follow-up undone':'Se deshizo el seguimiento de mañana','Fine status undone':'Se deshizo el estado “se ve bien”','Choose a pen first':'Primero elija un corral','Choose a diagnosis':'Elija un diagnóstico','Choose the animal color':'Elija el color del animal','Animal changes saved':'Cambios del animal guardados','Animal added to today':'Animal agregado al día de hoy','Checked today · entry moved to tomorrow':'Revisado hoy · registro pasado a mañana','Marked as fine':'Marcado como se ve bien','Marked pulled':'Marcado para retiro','Treatment updated':'Tratamiento actualizado','Treatment saved':'Tratamiento guardado','That category already exists':'Esa categoría ya existe','That option already exists':'Esa opción ya existe','Configuration saved':'Configuración guardada','Configuration imported':'Configuración importada','Backup restored':'Respaldo restaurado','Could not import configuration':'No se pudo importar la configuración','Could not import backup':'No se pudo importar el respaldo','Could not import animal data':'No se pudieron importar los datos de animales','Choose a valid Cowboy configuration JSON file.':'Elija un archivo JSON válido de configuración de Cowboy.','Choose a valid Cowboy JSON backup.':'Elija un respaldo JSON válido de Cowboy.','Choose a valid Cowboy animal data JSON file.':'Elija un archivo JSON válido de datos de animales de Cowboy.','Import feedlot setup?':'¿Importar configuración del corral?','Replace all Cowboy data?':'¿Reemplazar todos los datos de Cowboy?','Import animal records?':'¿Importar registros de animales?','Remove animal entry?':'¿Eliminar el registro del animal?','Delete configuration option':'Eliminar opción de configuración','Remove pen section':'Eliminar sección de corrales','Replace all data':'Reemplazar todos los datos','Remove section':'Eliminar sección','Confirm':'Confirmar','Select all':'Seleccionar todo','Choose month':'Elegir mes','Checking…':'Buscando…','Downloading…':'Descargando…','Checking for updates…':'Buscando actualizaciones…','Downloading app update…':'Descargando actualización…','Install app update':'Instalar actualización'
    }
  };
  const originals = new WeakMap(), applied = new WeakMap();
  const languageKey = 'cowboy-language';
  let language = localStorage.getItem(languageKey) || (navigator.language?.toLowerCase().startsWith('es') ? 'es' : 'en');
  const dynamic = [
    [/^Tag (.+) · Pen (.+)$/, 'Arete $1 · Corral $2'],[/^Pen: (.+)$/, 'Corral: $1'],[/^Pen (.+)$/, 'Corral $1'],[/^Tag (.+)$/, 'Arete $1'],[/^Diagnosis · (.+)$/, 'Diagnóstico · $1'],
    [/^Diagnosis: (.+)$/, 'Diagnóstico: $1'],[/^Color: (.+)$/, 'Color: $1'],[/^Last treated (.+)$/, 'Último tratamiento: $1'],
    [/^(\d+) animals?$/, '$1 animales'],[/^(\d+) pulls?$/, '$1 retiros'],[/^Checked (.+) · due today$/, 'Revisado $1 · vence hoy'],
    [/^Checked today · moved to (.+)$/, 'Revisado hoy · pasado a $1'],[/^Checked today · due (.+)$/, 'Revisado hoy · revisar $1'],[/^Follow-up from (.+)$/, 'Seguimiento de $1'],
    [/^Cowboy (.+) is available$/, 'Cowboy $1 está disponible'],[/^Cowboy (.+) has been downloaded\. Install it now or later\.$/, 'Cowboy $1 se descargó. Instálelo ahora o más tarde.'],
    [/^Remove the entry for (.+)\? This cannot be undone\.$/, '¿Eliminar el registro de $1? Esta acción no se puede deshacer.'],[/^App update unavailable: (.+)$/, 'Actualización de la aplicación no disponible: $1'],[/^Runtime update unavailable: (.+)$/, 'Actualización del sistema no disponible: $1'],[/^File: (.+)$/, 'Archivo: $1']
  ];
  const treatColorNames = {White:'Blanco',Green:'Verde',Yellow:'Amarillo',Red:'Rojo'};
  const diagnosisNames = {Respiratory:'Respiratorio',Footrot:'Pietín',Buller:'Toreado','Wrong Pen':'Corral equivocado',Other:'Otro'};
  const sectionNames = {East:'Este',West:'Oeste'};
  const animalColorNames = {Black:'Negro',Red:'Rojo',Gray:'Gris',Grey:'Gris',Brown:'Café'};
  const flagNames = {"Snotty nose":"Nariz con mucosidad","Head down":"Cabeza baja","Looks off":"Se ve decaído","Walks slow":"Camina lento","Redish eyes":"Ojos rojizos",Lame:'Cojo','Swollen foot':'Pata hinchada',Buller:'Toreado','Eye injury':'Lesión ocular','Not eating':'No come',Weak:'Débil',Dull:'Opaco',Thin:'Delgado',Swollen:'Hinchado',Wound:'Herida','Rough coat':'Pelaje áspero','Eye discharge':'Secreción ocular',Other:'Otro'};
  function translate(value) {
    if (language !== 'es') return value;
    const trimmed = value.trim();
    if (translations.es[trimmed]) return value.replace(trimmed, translations.es[trimmed]);
    const animalColor = trimmed.match(/^(Black|Red|Gray|Grey|Brown)(?: · Last treated (.+))?$/);
    if (animalColor) return value.replace(trimmed, `${animalColorNames[animalColor[1]]||animalColor[1]}${animalColor[2]?` · Último tratamiento: ${animalColor[2]}`:''}`);
    const treatTag = trimmed.match(/^Treat tag (White|Green|Yellow|Red) (.+)$/);
    if (treatTag) return value.replace(trimmed, `Arete de tratamiento ${treatColorNames[treatTag[1]]} ${treatTag[2]}`);
    const diagnosis = trimmed.match(/^Diagnosis(?:: | · )(.+)$/);
    if (diagnosis) return value.replace(trimmed, `${trimmed.startsWith('Diagnosis ·')?'Diagnóstico · ':'Diagnóstico: '}${diagnosisNames[diagnosis[1]]||diagnosis[1]}`);
    const selectedColor = trimmed.match(/^Color: (Black|Red|Gray|Grey|Brown)$/);
    if (selectedColor) return value.replace(trimmed, `Color: ${animalColorNames[selectedColor[1]]}`);
    for (const [pattern, replacement] of dynamic) if (pattern.test(trimmed)) return value.replace(trimmed, trimmed.replace(pattern, replacement));
    return value;
  }
  function translateTree(root) {
    if (!root) return;
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    let node;
    while ((node = walker.nextNode())) {
      if (!originals.has(node)) originals.set(node, node.nodeValue);
      else if (node.nodeValue !== applied.get(node)) originals.set(node, node.nodeValue);
      let translated = translate(originals.get(node));
      if(language==='es'){
        const source=originals.get(node).trim(),parent=node.parentElement;
        if(parent?.closest('.treat-color-choice'))translated=treatColorNames[source]||translated;
        else if(parent?.closest('#diagnosisChips'))translated=diagnosisNames[source]||translated;
        else if(parent?.closest('#colorChips'))translated=animalColorNames[source]||translated;
        else if(parent?.closest('#issueChips,#appearanceChips,.mini-chip'))translated=flagNames[source]||translated;
        else if(parent?.classList.contains('choice-section-title'))translated=sectionNames[source]||translated;
      }
      if (node.nodeValue !== translated) node.nodeValue = translated;
      applied.set(node, translated);
    }
    root.querySelectorAll?.('[placeholder],[aria-label],[title]').forEach(element => {
      for (const attribute of ['placeholder','aria-label','title']) {
        if (!element.hasAttribute(attribute)) continue;
        const key = `${attribute}`;
        let values = element.__cowboyI18nAttrs;
        if (!values) Object.defineProperty(element, '__cowboyI18nAttrs', {value:values={}});
        if (!(key in values)) values[key] = element.getAttribute(attribute);
        else if (element.getAttribute(attribute) !== values[`${key}Applied`]) values[key] = element.getAttribute(attribute);
        const value = translate(values[key]);
        element.setAttribute(attribute, value);
        values[`${key}Applied`] = value;
      }
    });
  }
  function apply() {
    document.documentElement.lang = language === 'es' ? 'es' : 'en';
    document.title = language === 'es' ? 'Cowboy · Control de salud del corral' : 'Cowboy · Feedlot Health Tracker';
    translateTree(document.body);
    const selector = document.querySelector('#languageSelect');
    if (selector) selector.value = language;
  }
  window.CowboyI18n = {
    get language(){return language;},
    locale(){return language === 'es' ? 'es' : undefined;},
    setLanguage(value){language = value === 'es' ? 'es' : 'en';localStorage.setItem(languageKey, language);apply();window.dispatchEvent(new Event('cowboylanguagechange'));},
    apply
  };
  document.addEventListener('DOMContentLoaded', apply);
  new MutationObserver(() => translateTree(document.body)).observe(document.documentElement, {subtree:true,childList:true,characterData:true});
})();
