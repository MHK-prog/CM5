const $ = id => document.getElementById(id);

const RC_FIELDS = Object.freeze([
  { key: 'cinv', label: 'Class Involvement', max: 40 },
  { key: 'spk', label: 'Speaking', max: 20 },
  { key: 'read', label: 'Reading', max: 20 },
  { key: 'hw', label: 'Homework', max: 20 },
  { key: 'mid', label: 'Midterm', max: 40 },
  { key: 'disc', label: 'Discipline', max: 10 },
  { key: 'list', label: 'Listening', max: 10 },
  { key: 'voc', label: 'Vocabulary', max: 10 },
  { key: 'spel', label: 'Spelling', max: 10 },
  { key: 'gram', label: 'Grammar', max: 10 },
  { key: 'wri', label: 'Writing', max: 10 }
]);

const AppStore = {
  key: 'cm5_report_cards_v1',
  state: null,

  uid() {
    return globalThis.crypto?.randomUUID?.() || `cm5_${Date.now()}_${Math.random().toString(16).slice(2)}`;
  },

  createStudent(firstName = '', lastName = '') {
    firstName = String(firstName).trim();
    lastName = String(lastName).trim();
    return {
      id: this.uid(), firstName, lastName, fullName: [firstName, lastName].filter(Boolean).join(' '), nationalId: '', fatherName: '', photo: '',
      reportCard: { scores: Array(RC_FIELDS.length).fill(''), teacherDesc: '' }
    };
  },

  createClass(name = 'کلاس جدید') {
    return { id: this.uid(), name: String(name).trim() || 'کلاس جدید', students: [] };
  },

  createFreshState() {
    const firstClass = this.createClass('کلاس جدید');
    return { version: 1, selectedClassId: firstClass.id, classes: [firstClass] };
  },

  normalizeState(input) {
    const source = input && typeof input === 'object' ? input : {};
    const classes = Array.isArray(source.classes) ? source.classes.map(rawClass => {
      const raw = rawClass && typeof rawClass === 'object' ? rawClass : {};
      const students = Array.isArray(raw.students) ? raw.students.map(rawStudent => {
        const student = rawStudent && typeof rawStudent === 'object' ? rawStudent : {};
        const report = student.reportCard && typeof student.reportCard === 'object' ? student.reportCard : {};
        const legacyNameParts = String(student.fullName || student.name || '').trim().split(/\s+/).filter(Boolean);
        const explicitFirstName = String(student.firstName || student.givenName || '').trim();
        const explicitLastName = String(student.lastName || student.familyName || '').trim();
        if (explicitFirstName && legacyNameParts[0] === explicitFirstName) legacyNameParts.shift();
        const firstName = explicitFirstName || legacyNameParts.shift() || '';
        const lastName = explicitLastName || legacyNameParts.join(' ');
        const fullName = [firstName, lastName].filter(Boolean).join(' ') || String(student.fullName || student.name || '').trim();
        const rawScores = Array.isArray(report.scores) ? report.scores : [];
        const scores = RC_FIELDS.map((field, index) => {
          const value = String(rawScores[index] ?? '').trim();
          if (!value) return '';
          const number = Number(normalizeNumber(value));
          return Number.isFinite(number) ? String(Math.min(field.max, Math.max(0, number))) : '';
        });
        return {
          id: String(student.id || this.uid()),
          firstName,
          lastName,
          fullName,
          nationalId: String(student.nationalId || ''),
          fatherName: String(student.fatherName || ''),
          photo: typeof student.photo === 'string' ? student.photo : '',
          reportCard: { scores, teacherDesc: String(report.teacherDesc || '') }
        };
      }) : [];
      return { id: String(raw.id || this.uid()), name: String(raw.name || 'کلاس جدید'), students };
    }) : [];

    const finalClasses = classes.length ? classes : this.createFreshState().classes;
    const requestedId = String(source.selectedClassId || '');
    const selectedClassId = finalClasses.some(item => item.id === requestedId) ? requestedId : finalClasses[0].id;
    return { version: 1, selectedClassId, classes: finalClasses };
  },

  load() {
    try {
      const raw = localStorage.getItem(this.key);
      this.state = this.normalizeState(raw ? JSON.parse(raw) : null);
    } catch {
      this.state = this.createFreshState();
    }
    return this.state;
  },

  save() {
    if (!this.state) this.state = this.createFreshState();
    try {
      localStorage.setItem(this.key, JSON.stringify(this.state));
      if ($('saveStateText')) $('saveStateText').textContent = 'ذخیره شد';
      return true;
    } catch {
      if ($('saveStateText')) $('saveStateText').textContent = 'فضای ذخیره پر است';
      toast('ذخیره انجام نشد؛ حجم عکس‌ها را کم کنید.');
      return false;
    }
  },

  replace(input) {
    this.state = this.normalizeState(input);
    this.save();
    return this.state;
  },

  getSelectedClass() {
    if (!this.state?.classes?.length) return null;
    const selected = this.state.classes.find(item => item.id === this.state.selectedClassId) || this.state.classes[0];
    this.state.selectedClassId = selected.id;
    return selected;
  },

  reset() {
    this.state = this.createFreshState();
    this.save();
  }
};

function normalizeNumber(value) {
  const digits = { '۰':'0', '۱':'1', '۲':'2', '۳':'3', '۴':'4', '۵':'5', '۶':'6', '۷':'7', '۸':'8', '۹':'9', '٠':'0', '١':'1', '٢':'2', '٣':'3', '٤':'4', '٥':'5', '٦':'6', '٧':'7', '٨':'8', '٩':'9', '٫':'.', '،':'.', ',':'.' };
  return String(value ?? '').replace(/[۰-۹٠-٩٫،,]/g, char => digits[char] || char).trim();
}

function toast(message) {
  const box = $('toast');
  if (!box) return;
  $('toastMsg').textContent = message;
  box.style.display = 'flex';
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { box.style.display = 'none'; }, 2800);
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('fa-IR');
}

function closeMenu() {
  $('menuDrawer')?.classList.remove('open');
  syncOverlay();
}

function syncOverlay() {
  const visible = $('menuDrawer')?.classList.contains('open') || document.querySelector('.modal.active');
  $('modalOverlay').style.display = visible ? 'block' : 'none';
  document.body.classList.toggle('modal-open', Boolean(visible));
}

function openModal(id) {
  document.querySelectorAll('.modal.active').forEach(modal => modal.classList.remove('active'));
  closeMenu();
  $(id)?.classList.add('active');
  syncOverlay();
}

function closeModal(id) {
  const closesStudentModal = id
    ? id === 'studentModal'
    : Boolean($('studentModal')?.classList.contains('active'));
  if (closesStudentModal) {
    studentModalSession++;
    photoLoadId++;
    pendingPhotoLoad = Promise.resolve();
  }
  if (id) $(id)?.classList.remove('active');
  else document.querySelectorAll('.modal.active').forEach(modal => modal.classList.remove('active'));
  syncOverlay();
}

function showPage(pageId) {
  document.querySelectorAll('.page').forEach(page => page.classList.toggle('active', page.id === pageId));
  document.querySelectorAll('.tab').forEach(tab => tab.classList.toggle('active', tab.dataset.page === pageId));
  closeMenu();
  if (pageId === 'page-classes') renderClasses();
  if (pageId === 'page-students') renderStudentsManage();
  if (pageId === 'page-report-cards') initReportCardPage();
}

function renderClasses() {
  const select = $('classSelect');
  const classes = AppStore.state.classes;
  $('classesCount').textContent = `${classes.length} کلاس`;
  select.replaceChildren();
  classes.forEach(item => {
    const option = new Option(item.name || 'کلاس', item.id);
    option.selected = item.id === AppStore.state.selectedClassId;
    select.appendChild(option);
  });
}

function refreshCurrentClass() {
  AppStore.save();
  renderClasses();
  renderStudentsManage();
  initReportCardPage();
}

let classModalMode = 'add';
function openClassModal(mode) {
  classModalMode = mode;
  const current = AppStore.getSelectedClass();
  $('classModalTitle').textContent = mode === 'add' ? 'کلاس جدید' : 'تغییر نام کلاس';
  $('classNameInput').value = mode === 'add' ? '' : current?.name || '';
  $('saveClassModal').textContent = mode === 'add' ? 'ثبت کلاس' : 'ذخیره تغییر';
  openModal('classModal');
  setTimeout(() => $('classNameInput').focus(), 60);
}

function saveClassModal() {
  const name = $('classNameInput').value.trim();
  if (!name) return toast('نام کلاس را وارد کنید.');
  if (classModalMode === 'add') {
    const item = AppStore.createClass(name);
    AppStore.state.classes.push(item);
    AppStore.state.selectedClassId = item.id;
  } else {
    const current = AppStore.getSelectedClass();
    if (current) current.name = name;
  }
  closeModal('classModal');
  refreshCurrentClass();
  toast(classModalMode === 'add' ? 'کلاس اضافه شد.' : 'نام کلاس تغییر کرد.');
}

function deleteCurrentClass() {
  const state = AppStore.state;
  if (state.classes.length <= 1) return toast('حداقل یک کلاس باید باقی بماند.');
  if (!confirm('این کلاس و زبان‌آموزان آن حذف شوند؟')) return;
  state.classes = state.classes.filter(item => item.id !== state.selectedClassId);
  state.selectedClassId = state.classes[0].id;
  refreshCurrentClass();
  toast('کلاس حذف شد.');
}

function renderStudentsManage() {
  const cls = AppStore.getSelectedClass();
  const list = $('studentsManageList');
  if (!cls || !list) return;
  $('studentsCount').textContent = `${cls.students.length} نفر`;
  $('studentsEmpty').hidden = cls.students.length > 0;
  list.replaceChildren();

  cls.students.forEach((student, index) => {
    const row = document.createElement('div');
    row.className = 'student-row';
    const number = document.createElement('span');
    number.className = 'student-number';
    number.textContent = String(index + 1);
    const name = document.createElement('strong');
    name.className = 'student-name';
    name.textContent = student.fullName || 'بدون نام';
    const info = document.createElement('button');
    info.type = 'button';
    info.className = 'btn student-edit';
    info.textContent = 'مشخصات';
    info.addEventListener('click', () => openStudentModal(student.id));
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'btn pink student-remove';
    remove.textContent = 'حذف';
    remove.addEventListener('click', () => {
      if (!confirm(`زبان‌آموز «${student.fullName || 'بدون نام'}» حذف شود؟`)) return;
      cls.students = cls.students.filter(item => item.id !== student.id);
      AppStore.save();
      renderStudentsManage();
      initReportCardPage();
      toast('زبان‌آموز حذف شد.');
    });
    row.append(number, name, info, remove);
    list.appendChild(row);
  });
}

let editingStudentId = null;
let pendingPhoto = '';
let photoLoadId = 0;
let studentModalSession = 0;
let pendingPhotoLoad = Promise.resolve();

function openStudentModal(studentId = null) {
  const student = studentId
    ? AppStore.getSelectedClass()?.students.find(item => item.id === studentId)
    : null;
  if (studentId && !student) return;
  studentModalSession++;
  editingStudentId = student?.id || null;
  photoLoadId++;
  pendingPhotoLoad = Promise.resolve();
  pendingPhoto = student?.photo || '';
  $('studentModalTitle').textContent = student ? 'ویرایش زبان‌آموز' : 'افزودن زبان‌آموز';
  $('profileFirstName').value = student?.firstName || '';
  $('profileLastName').value = student?.lastName || '';
  $('profileNationalId').value = student?.nationalId || '';
  $('profileFatherName').value = student?.fatherName || '';
  $('profilePhoto').value = '';
  updatePhotoPreview();
  openModal('studentModal');
  setTimeout(() => $('profileFirstName').focus(), 60);
}

function updatePhotoPreview() {
  const image = $('profilePhotoPreview');
  const placeholder = $('profilePhotoPlaceholder');
  const removeButton = $('removeStudentPhoto');
  image.hidden = !pendingPhoto;
  image.src = pendingPhoto || '';
  placeholder.hidden = Boolean(pendingPhoto);
  removeButton.hidden = !pendingPhoto;
  $('chooseStudentPhoto').textContent = pendingPhoto ? 'تغییر عکس' : 'انتخاب یا گرفتن عکس';
}

function imageFileToDataUrl(file) {
  return new Promise((resolve, reject) => {
    const objectUrl = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      const width = 480;
      const height = 600;
      const scale = Math.max(width / image.naturalWidth, height / image.naturalHeight);
      const sourceWidth = width / scale;
      const sourceHeight = height / scale;
      const sourceX = (image.naturalWidth - sourceWidth) / 2;
      const sourceY = (image.naturalHeight - sourceHeight) / 2;
      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      const context = canvas.getContext('2d');
      context.fillStyle = '#ffffff';
      context.fillRect(0, 0, width, height);
      context.drawImage(image, sourceX, sourceY, sourceWidth, sourceHeight, 0, 0, width, height);
      URL.revokeObjectURL(objectUrl);
      resolve(canvas.toDataURL('image/jpeg', 0.78));
    };
    image.onerror = () => { URL.revokeObjectURL(objectUrl); reject(new Error('image')); };
    image.src = objectUrl;
  });
}

function loadStudentPhoto(file) {
  const cameraToken = window.CM5?.takeCameraPhotoToken?.() || '';
  const requestId = ++photoLoadId;
  pendingPhotoLoad = imageFileToDataUrl(file).then(dataUrl => {
    if (requestId !== photoLoadId) return;
    pendingPhoto = dataUrl;
    updatePhotoPreview();
  }).catch(() => {
    if (requestId === photoLoadId) toast('خواندن عکس انجام نشد.');
  }).finally(() => {
    if (cameraToken) window.CM5?.finishCameraPhoto?.(cameraToken);
  });
  return pendingPhotoLoad;
}

async function saveStudentModal() {
  if ($('saveStudentModal').disabled) return;
  const session = studentModalSession;
  const saveButton = $('saveStudentModal');
  saveButton.disabled = true;
  try {
    await pendingPhotoLoad;
    if (session !== studentModalSession || !$('studentModal').classList.contains('active')) return;
    const cls = AppStore.getSelectedClass();
    if (!cls) return closeModal('studentModal');
    const firstName = $('profileFirstName').value.trim();
    const lastName = $('profileLastName').value.trim();
    if (!firstName || !lastName) return toast('نام و نام خانوادگی را وارد کنید.');
    let student = editingStudentId ? cls.students.find(item => item.id === editingStudentId) : null;
    if (editingStudentId && !student) return closeModal('studentModal');
    if (!student) {
      student = AppStore.createStudent(firstName, lastName);
      cls.students.push(student);
    }
    student.firstName = firstName;
    student.lastName = lastName;
    student.fullName = `${firstName} ${lastName}`;
    student.nationalId = $('profileNationalId').value.trim();
    student.fatherName = $('profileFatherName').value.trim();
    student.photo = pendingPhoto;
    AppStore.save();
    closeModal('studentModal');
    renderStudentsManage();
    initReportCardPage();
    toast('مشخصات ذخیره شد.');
  } finally {
    saveButton.disabled = false;
  }
}

let rcCurrentMode = 'student';
function getRcTotal(student) {
  return (student?.reportCard?.scores || []).reduce((sum, value) => sum + (Number(normalizeNumber(value)) || 0), 0);
}

function initReportCardPage() {
  const cls = AppStore.getSelectedClass();
  if (!cls) return;
  $('rcActiveClassName').textContent = cls.name || '';
  const studentSelect = $('rcStudentSelect');
  const currentId = studentSelect.value;
  studentSelect.replaceChildren();
  cls.students.forEach((student, index) => {
    const option = new Option(`(${index + 1}/${cls.students.length}) ${student.fullName || 'بدون نام'}`, student.id);
    studentSelect.appendChild(option);
  });
  if (cls.students.some(student => student.id === currentId)) studentSelect.value = currentId;

  const fieldSelect = $('rcFieldSelect');
  if (!fieldSelect.options.length) RC_FIELDS.forEach((field, index) => fieldSelect.add(new Option(`${field.label} (${field.max})`, String(index))));
  renderRcMode();
}

function setRcMode(mode) {
  rcCurrentMode = mode;
  renderRcMode();
}

function renderRcMode() {
  const isStudent = rcCurrentMode === 'student';
  $('rcStudentContainer').hidden = !isStudent;
  $('rcFieldContainer').hidden = isStudent;
  $('btnRcModeStudent').classList.toggle('primary', isStudent);
  $('btnRcModeField').classList.toggle('primary', !isStudent);
  if (isStudent) renderRcStudentView();
  else renderRcFieldView();
}

function makeScoreInput(student, fieldIndex) {
  const field = RC_FIELDS[fieldIndex];
  const input = document.createElement('input');
  input.type = 'text';
  input.inputMode = 'decimal';
  input.className = 'num rc-input';
  input.dataset.studentId = student.id;
  input.dataset.fieldIndex = String(fieldIndex);
  input.placeholder = `۰ تا ${formatNumber(field.max)}`;
  input.value = student.reportCard.scores[fieldIndex] || '';
  input.setAttribute('aria-label', `${student.fullName || 'زبان‌آموز'}، ${field.label}`);
  input.addEventListener('input', () => {
    const value = normalizeNumber(input.value);
    if (!value) student.reportCard.scores[fieldIndex] = '';
    else {
      const numeric = Number(value);
      student.reportCard.scores[fieldIndex] = Number.isFinite(numeric) ? String(Math.min(field.max, Math.max(0, numeric))) : '';
    }
    AppStore.save();
    if (rcCurrentMode === 'student') updateRcTotal(student);
    if (!$('reportPreviewPanel').hidden) renderReportCard(student);
  });
  return input;
}

function renderRcStudentView() {
  const cls = AppStore.getSelectedClass();
  const list = $('rcStudentFormList');
  const student = cls?.students.find(item => item.id === $('rcStudentSelect').value);
  list.replaceChildren();
  if (!student) {
    const empty = document.createElement('div');
    empty.className = 'empty-state';
    empty.textContent = 'ابتدا از بخش زبان‌آموزان، نام‌ها را وارد کنید.';
    list.appendChild(empty);
    $('rcCurrentTotal').textContent = '۰ / ۲۰۰';
    return;
  }
  RC_FIELDS.forEach((field, index) => {
    const row = document.createElement('div');
    row.className = 'score-row';
    const label = document.createElement('label');
    label.textContent = `${field.label} (${formatNumber(field.max)})`;
    row.append(label, makeScoreInput(student, index));
    list.appendChild(row);
  });
  const description = document.createElement('label');
  description.className = 'teacher-label';
  description.textContent = 'نظر مدرس';
  const textarea = document.createElement('textarea');
  textarea.className = 'teacher-description';
  textarea.dataset.studentId = student.id;
  textarea.value = student.reportCard.teacherDesc || '';
  textarea.placeholder = 'توضیحات مدرس درباره‌ی پیشرفت زبان‌آموز';
  textarea.addEventListener('input', () => {
    student.reportCard.teacherDesc = textarea.value;
    AppStore.save();
    if (!$('reportPreviewPanel').hidden) renderReportCard(student);
  });
  list.append(description, textarea);
  updateRcTotal(student);
}

function renderRcFieldView() {
  const cls = AppStore.getSelectedClass();
  const index = Number($('rcFieldSelect').value || 0);
  const list = $('rcFieldFormList');
  list.replaceChildren();
  if (!cls?.students.length) {
    const empty = document.createElement('div');
    empty.className = 'empty-state';
    empty.textContent = 'ابتدا از بخش زبان‌آموزان، نام‌ها را وارد کنید.';
    list.appendChild(empty);
    return;
  }
  cls.students.forEach(student => {
    const row = document.createElement('div');
    row.className = 'score-row field-score-row';
    const name = document.createElement('strong');
    name.textContent = student.fullName || 'بدون نام';
    row.append(name, makeScoreInput(student, index));
    list.appendChild(row);
  });
}

function updateRcTotal(student) {
  const max = RC_FIELDS.reduce((sum, field) => sum + field.max, 0);
  $('rcCurrentTotal').textContent = `${formatNumber(getRcTotal(student))} / ${formatNumber(max)}`;
}

function navRcStudent(delta) {
  const select = $('rcStudentSelect');
  if (!select.options.length) return;
  select.selectedIndex = (select.selectedIndex + delta + select.options.length) % select.options.length;
  renderRcStudentView();
}

function appendInfoItem(container, label, value) {
  const item = document.createElement('div');
  const caption = document.createElement('span');
  caption.textContent = `${label}: `;
  const text = document.createElement('strong');
  text.textContent = value || '—';
  item.append(caption, text);
  container.appendChild(item);
}

function renderReportCard(student) {
  const card = $('reportCard');
  card.replaceChildren();
  const max = RC_FIELDS.reduce((sum, field) => sum + field.max, 0);
  const total = getRcTotal(student);

  const header = document.createElement('div');
  header.className = 'report-header';
  const logo = document.createElement('img');
  logo.className = 'report-logo';
  logo.src = 'img/logo.webp';
  logo.alt = 'لوگوی آموزشگاه';
  const school = document.createElement('div');
  school.className = 'school-info';
  const schoolName = document.createElement('h3');
  schoolName.textContent = 'آموزشگاه زبان دماوند';
  const schoolSubtitle = document.createElement('p');
  schoolSubtitle.textContent = 'کارنامه ارزشیابی دوره ترمیک';
  school.append(schoolName, schoolSubtitle);
  const photo = document.createElement('img');
  photo.className = 'report-photo';
  photo.alt = 'عکس زبان‌آموز';
  if (student.photo) photo.src = student.photo;
  else photo.classList.add('no-photo');
  header.append(logo, school, photo);
  card.appendChild(header);

  const details = document.createElement('div');
  details.className = 'report-info-grid';
  appendInfoItem(details, 'نام زبان‌آموز', student.fullName);
  appendInfoItem(details, 'کلاس / سطح', AppStore.getSelectedClass()?.name || '');
  appendInfoItem(details, 'کد ملی', student.nationalId);
  appendInfoItem(details, 'نام پدر', student.fatherName);
  card.appendChild(details);

  const table = document.createElement('table');
  table.className = 'report-table';
  const head = document.createElement('thead');
  const headRow = document.createElement('tr');
  ['#', 'مهارت / مبحث', 'حداکثر', 'نمره'].forEach((title, index) => {
    const cell = document.createElement('th');
    cell.textContent = title;
    if (index === 0) cell.className = 'report-number-cell';
    headRow.appendChild(cell);
  });
  head.appendChild(headRow);
  table.appendChild(head);
  const body = document.createElement('tbody');
  RC_FIELDS.forEach((field, index) => {
    const row = document.createElement('tr');
    const number = document.createElement('td');
    number.textContent = formatNumber(index + 1);
    const label = document.createElement('td');
    label.className = 'report-field-name';
    label.textContent = field.label;
    const maxCell = document.createElement('td');
    maxCell.textContent = formatNumber(field.max);
    const scoreCell = document.createElement('td');
    scoreCell.textContent = student.reportCard.scores[index] ? formatNumber(student.reportCard.scores[index]) : '';
    row.append(number, label, maxCell, scoreCell);
    body.appendChild(row);
  });
  const totalRow = document.createElement('tr');
  totalRow.className = 'report-total-row';
  const totalLabel = document.createElement('td');
  totalLabel.colSpan = 2;
  totalLabel.textContent = 'مجموع نمرات';
  const totalMax = document.createElement('td');
  totalMax.textContent = formatNumber(max);
  const totalScore = document.createElement('td');
  totalScore.textContent = formatNumber(total);
  totalRow.append(totalLabel, totalMax, totalScore);
  body.appendChild(totalRow);
  table.appendChild(body);
  card.appendChild(table);

  const comment = document.createElement('div');
  comment.className = 'report-comment';
  const commentLabel = document.createElement('strong');
  commentLabel.textContent = 'نظر مدرس:';
  const commentText = document.createElement('span');
  commentText.textContent = student.reportCard.teacherDesc || '—';
  comment.append(commentLabel, commentText);
  card.appendChild(comment);

  const footer = document.createElement('div');
  footer.className = 'report-footer';
  const teacherSign = document.createElement('span');
  teacherSign.textContent = 'امضای مدرس: ........................';
  const instituteSeal = document.createElement('span');
  instituteSeal.textContent = 'مهر آموزشگاه: ........................';
  footer.append(teacherSign, instituteSeal);
  card.appendChild(footer);
}

function openReportPreview() {
  const student = AppStore.getSelectedClass()?.students.find(item => item.id === $('rcStudentSelect').value);
  if (!student) return toast('ابتدا یک زبان‌آموز انتخاب کنید.');
  renderReportCard(student);
  $('reportPreviewPanel').hidden = false;
  $('reportPreviewPanel').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function selectedReportStudent() {
  return AppStore.getSelectedClass()?.students.find(item => item.id === $('rcStudentSelect').value);
}

function safeFilename(name) {
  return String(name || 'زبان‌آموز').replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_').replace(/\s+/g, '_').slice(0, 60);
}

function saveDataUrl(dataUrl, fileName, mimeType) {
  const base64 = dataUrl.split(',')[1] || '';
  if (window.CM5?.saveFile) {
    const result = window.CM5.saveFile(fileName, mimeType, base64);
    if (result !== 'saved') throw new Error(result || 'save failed');
    return;
  }
  const anchor = document.createElement('a');
  anchor.href = dataUrl;
  anchor.download = fileName;
  anchor.click();
}

async function exportReportPng() {
  const student = selectedReportStudent();
  if (!student) return toast('ابتدا یک زبان‌آموز انتخاب کنید.');
  toast('در حال ساخت تصویر کارنامه…');
  try {
    const canvas = await captureReportCanvas();
    saveDataUrl(canvas.toDataURL('image/png'), `CM5_${safeFilename(student.fullName)}.png`, 'image/png');
    toast('تصویر کارنامه ذخیره شد.');
  } catch {
    toast('ساخت تصویر انجام نشد.');
  }
}

async function captureReportCanvas() {
  await document.fonts?.ready;
  return window.html2canvas($('reportCard'), {
    backgroundColor: '#ffffff',
    scale: 3,
    useCORS: false,
    logging: false
  });
}

async function saveReportPdf() {
  const student = selectedReportStudent();
  if (!student) return toast('ابتدا یک زبان‌آموز انتخاب کنید.');
  if (!window.CM5?.saveReportPdf) return toast('ذخیرهٔ PDF مستقیم در نسخهٔ اندروید در دسترس است.');
  toast('در حال ساخت PDF…');
  try {
    const canvas = await captureReportCanvas();
    const base64 = canvas.toDataURL('image/png').split(',')[1] || '';
    const result = window.CM5.saveReportPdf(`CM5_${safeFilename(student.fullName)}.pdf`, base64);
    if (result !== 'saved') throw new Error(result || 'save failed');
    toast('PDF در پوشهٔ بارگیری / CM5 ذخیره شد.');
  } catch {
    toast('ساخت یا ذخیرهٔ PDF انجام نشد.');
  }
}

function downloadText(content, fileName, mimeType) {
  if (window.CM5?.saveFile) {
    const dataUrl = `data:${mimeType};base64,${btoa(unescape(encodeURIComponent(content)))}`;
    saveDataUrl(dataUrl, fileName, mimeType);
    return;
  }
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  anchor.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function backup() {
  downloadText(JSON.stringify(AppStore.state, null, 2), 'CM5_backup.json', 'application/json;charset=utf-8');
  toast('فایل پشتیبان آماده شد.');
}

function readFileText(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ''));
    reader.onerror = () => reject(reader.error || new Error('read error'));
    reader.readAsText(file, 'utf-8');
  });
}

async function restoreBackup(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  try {
    const imported = JSON.parse(await readFileText(file));
    if (!Array.isArray(imported.classes)) throw new Error('missing classes');
    AppStore.replace(imported);
    $('reportPreviewPanel').hidden = true;
    renderClasses();
    renderStudentsManage();
    initReportCardPage();
    toast('کلاس‌ها و کارنامه‌ها بازیابی شدند.');
  } catch {
    toast('فایل پشتیبان معتبر نیست.');
  } finally {
    event.target.value = '';
  }
}

function wipeData() {
  if (!confirm('همه‌ی کلاس‌ها، زبان‌آموزان و کارنامه‌ها حذف شوند؟')) return;
  AppStore.reset();
  $('reportPreviewPanel').hidden = true;
  renderClasses();
  renderStudentsManage();
  initReportCardPage();
  toast('داده‌ها حذف شدند.');
}

function bindEvents() {
  $('menuBtn').addEventListener('click', () => {
    $('menuDrawer').classList.toggle('open');
    syncOverlay();
  });
  $('modalOverlay').addEventListener('click', () => { closeMenu(); closeModal(); });
  $('toastX').addEventListener('click', () => { $('toast').style.display = 'none'; });
  document.addEventListener('keydown', event => { if (event.key === 'Escape') { closeMenu(); closeModal(); } });
  document.querySelectorAll('.tab').forEach(button => button.addEventListener('click', () => showPage(button.dataset.page)));
  document.querySelectorAll('[data-go]').forEach(button => button.addEventListener('click', () => showPage(button.dataset.go)));
  $('quickExport').addEventListener('click', () => showPage('page-export'));
  $('classSelect').addEventListener('change', event => {
    AppStore.state.selectedClassId = event.target.value;
    AppStore.save();
    renderStudentsManage();
    initReportCardPage();
  });
  $('btnAddClass').addEventListener('click', () => openClassModal('add'));
  $('btnRenameClass').addEventListener('click', () => openClassModal('rename'));
  $('btnDeleteClass').addEventListener('click', deleteCurrentClass);
  $('saveClassModal').addEventListener('click', saveClassModal);
  $('cancelClassModal').addEventListener('click', () => closeModal('classModal'));
  $('classNameInput').addEventListener('keydown', event => { if (event.key === 'Enter') saveClassModal(); });
  $('btnAddStudent').addEventListener('click', () => openStudentModal());
  $('saveStudentModal').addEventListener('click', saveStudentModal);
  $('cancelStudentModal').addEventListener('click', () => closeModal('studentModal'));
  $('chooseStudentPhoto').addEventListener('click', () => $('profilePhoto').click());
  $('profilePhoto').addEventListener('change', async event => {
    const file = event.target.files?.[0];
    if (!file) return;
    await loadStudentPhoto(file);
  });
  $('removeStudentPhoto').addEventListener('click', () => {
    photoLoadId++;
    pendingPhotoLoad = Promise.resolve();
    pendingPhoto = '';
    $('profilePhoto').value = '';
    updatePhotoPreview();
  });
  $('btnRcModeStudent').addEventListener('click', () => setRcMode('student'));
  $('btnRcModeField').addEventListener('click', () => setRcMode('field'));
  $('rcStudentSelect').addEventListener('change', renderRcStudentView);
  document.querySelectorAll('.nav-student').forEach(button => button.addEventListener('click', () => navRcStudent(Number(button.dataset.delta))));
  $('rcFieldSelect').addEventListener('change', renderRcFieldView);
  $('btnPreviewReport').addEventListener('click', openReportPreview);
  $('btnBackToEditor').addEventListener('click', () => $('reportPreviewPanel').hidden = true);
  $('btnSavePng').addEventListener('click', exportReportPng);
  $('btnSavePdf').addEventListener('click', saveReportPdf);
  $('btnBackup').addEventListener('click', backup);
  $('btnRestore').addEventListener('click', () => $('restoreFile').click());
  $('restoreFile').addEventListener('change', restoreBackup);
  $('btnWipe').addEventListener('click', wipeData);

  let touchX = 0;
  let touchY = 0;
  document.addEventListener('touchstart', event => {
    touchX = event.touches[0]?.clientX || 0;
    touchY = event.touches[0]?.clientY || 0;
  }, { passive: true });
  document.addEventListener('touchend', event => {
    const touch = event.changedTouches[0];
    if (!touch || touchX < window.innerWidth * 0.65) return;
    if (touchX - touch.clientX > 55 && Math.abs(touchY - touch.clientY) < 80) {
      $('menuDrawer').classList.add('open');
      syncOverlay();
    }
  }, { passive: true });
}

AppStore.load();
bindEvents();
renderClasses();
renderStudentsManage();
initReportCardPage();
