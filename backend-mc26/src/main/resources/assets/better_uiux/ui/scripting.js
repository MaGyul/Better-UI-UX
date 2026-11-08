// 스크립트 데모: `ui` 객체로 XML 요소와 CSS 변수를 바꾸고, 허용된 마인크래프트 명령어를 실행한다.
// 호스트와는 JSON 데이터만 오가며, 자바 클래스/파일/네트워크에는 접근할 수 없다.
var clicks = 0;

function onInit(state) {
  ui.setText('#status', '준비됨 — 이름: ' + state.name.value);
}

function greet(ev, state) {
  clicks++;
  var name = state.name.value || '이름 없음';
  ui.setText('#status', name + ' 님, ' + clicks + '번 눌렀어요');
  ui.setVar('accent', clicks % 2 ? '#FF55FF55' : '#FFFFAA00');   // CSS 변수 var(--accent)
  ui.toggleClass('#status', 'hot', clicks % 2 === 0);
  ui.command('say ' + name + ' clicked ' + clicks);               // config 의 scripting.commands 에 say 가 있어야 실행된다
}

function addRow(ev, state) {
  ui.append('#list', '<label text="추가된 줄 ' + (++clicks) + '"/>');
}

function clearRows(ev, state) {
  ui.remove('#list > label');
}

function volume(ev, state) {
  ui.setText('#status', '볼륨 ' + state.volume.value);
  ui.log('volume changed', state.volume.value);
}
