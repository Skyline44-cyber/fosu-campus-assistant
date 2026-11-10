(function () {
  function docs() {
    var list = [document];
    var seen = [document];
    var i = 0;
    while (i < list.length) {
      var frames = list[i].querySelectorAll('iframe,frame');
      for (var j = 0; j < frames.length; j++) {
        try {
          var d = frames[j].contentDocument;
          if (d && seen.indexOf(d) < 0) {
            seen.push(d);
            list.push(d);
          }
        } catch (e) {
        }
      }
      i++;
    }
    return list;
  }

  function clean(value) {
    return String(value || '')
      .replace(/\u00a0/g, ' ')
      .replace(/\r/g, '')
      .replace(/[ \t]+/g, ' ')
      .replace(/\n{2,}/g, '\n')
      .trim();
  }

  function lines(value) {
    return clean(value).split('\n').map(function (x) { return x.trim(); }).filter(function (x) { return x.length > 0; });
  }

  function text(el) {
    return el ? clean(el.innerText || el.textContent || '') : '';
  }

  function spacedText(el) {
    if (!el) return '';
    var parts = [];
    function walk(node) {
      if (!node) return;
      if (node.nodeType === 3) {
        var value = clean(node.nodeValue);
        if (value) parts.push(value);
        return;
      }
      if (node.nodeName === 'BR') {
        parts.push('\n');
        return;
      }
      for (var i = 0; i < node.childNodes.length; i++) walk(node.childNodes[i]);
    }
    walk(el);
    return parts.join(' ');
  }

  function visible(el) {
    if (!el) return false;
    var rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
    return !!rect && rect.width > 0 && rect.height > 0;
  }

  function weekday(value) {
    var s = String(value || '').replace(/\s/g, '');
    var table = { '一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '日': 7, '天': 7 };
    var m = s.match(/(?:星期|周|礼拜)([一二三四五六日天])/);
    if (m) return table[m[1]];
    m = s.match(/^([1-7])$/);
    if (m) return Number(m[1]);
    m = s.match(/(?:星期|周|礼拜)([1-7])/);
    if (m) return Number(m[1]);
    return 0;
  }

  function cnNumber(s) {
    var map = { '一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '七': 7, '八': 8, '九': 9, '十': 10 };
    var total = 0;
    var current = 0;
    for (var i = 0; i < s.length; i++) {
      var ch = s.charAt(i);
      if (ch === '十') {
        if (current === 0) current = 1;
        total += current * 10;
        current = 0;
      } else if (map[ch]) {
        current = map[ch];
      }
    }
    return total + current;
  }

  function sections(value) {
    var s = String(value || '').replace(/\s/g, '');
    var m = s.match(/(\d{1,2})[-~至](\d{1,2})/);
    if (m) return [Number(m[1]), Number(m[2])];
    m = s.match(/^0?(\d{2})0?(\d{2})$/);
    if (m) return [Number(m[1]), Number(m[2])];
    m = s.match(/0?(\d{2})0?(\d{2})0?(\d{2})/);
    if (m) return [Number(m[1]), Number(m[3])];
    m = s.match(/(\d{1,2})/);
    if (m) return [Number(m[1]), Number(m[1])];
    var cn = s.match(/第?([一二三四五六七八九十]{1,3})节?/);
    if (cn) {
      var n = cnNumber(cn[1]);
      if (n > 0) return [n, n];
    }
    return null;
  }

  function normalizeWeeks(value) {
    var s = String(value || '');
    var m = s.match(/((?:\d{1,2}\s*[-~至]\s*\d{1,2}|\d{1,2})(?:\s*[,，]\s*(?:\d{1,2}\s*[-~至]\s*\d{1,2}|\d{1,2}))*)/);
    if (!m) return '';
    var result = m[1].replace(/\s/g, '').replace(/[~至]/g, '-');
    if (/单/.test(s)) result += '单';
    else if (/双/.test(s)) result += '双';
    return result;
  }

  function weeks(value) {
    var s = String(value || '');
    if (!/周/.test(s)) return '';
    var m = s.match(/(?:^|[^\d])((?:\d{1,2}\s*[-~至]\s*\d{1,2})(?:\s*[,，]\s*(?:\d{1,2}\s*[-~至]\s*\d{1,2}))*)\s*周/);
    if (!m) return '';
    var result = m[1].replace(/\s/g, '').replace(/[~至]/g, '-');
    if (/单/.test(s)) result += '单';
    else if (/双/.test(s)) result += '双';
    return result;
  }

  function pick(el, selector) {
    var found = el ? el.querySelector(selector) : null;
    return found ? text(found) : '';
  }

  function isJunk(name) {
    if (!name) return true;
    if (name.length > 60) return true;
    if (/^(课表|课表查询|学生课表|我的课表|班级课表|教师课表|教室课表|查询|打印|返回)$/.test(name)) return true;
    if (/^(星期|周一|周二|周三|周四|周五|周六|周日|节次|课程)$/.test(name)) return true;
    return false;
  }

  function make(name, teacher, room, weekDay, start, end, weekSpec) {
    name = clean(name).replace(/^(课程名称|课程名|课程)[:：]?/, '').trim();
    if (isJunk(name)) return null;
    if (!weekDay || !start || !end) return null;
    start = Number(start);
    end = Number(end);
    if (start < 1 || end < start || end > 20) return null;
    return {
      name: name,
      teacher: clean(teacher).slice(0, 30),
      room: clean(room).slice(0, 30),
      weekday: weekDay,
      start: start,
      end: end,
      weeks: (weekSpec || '1-16').slice(0, 40)
    };
  }

  function courseFrom(el, weekDay, start, end) {
    var full = text(el);
    if (!full) return null;
    var flat = spacedText(el);
    var name = pick(el, '.title,.course-name,.courseName,.kcmc,[class*=title]');
    var teacher = pick(el, '.teacher,.teacher-name,.teacherName,.jsxm,[class*=teacher]');
    var room = pick(el, '.room,.place,.location,.cdmc,[class*=room]');
    var weekText = pick(el, '.week,.weeks,.week-text,.weekText,.zcd,[class*=week]');
    var weekSpec = normalizeWeeks(weekText) || weeks(flat) || weeks(full) || '1-16';
    var ls = lines(full);
    if (ls.length <= 1 && flat) ls = flat.split(/\s+/).filter(function (x) { return x.length > 0; });
    if (!name) name = ls.length ? ls[0] : '';
    name = clean(name).replace(/^课程(名称)?[:：]/, '').trim();
    if (!teacher) {
      for (var i = 1; i < Math.min(ls.length, 4); i++) {
        var line = ls[i];
        if (/周/.test(line) || /[0-9]/.test(line) || /楼|室|校区|教室/.test(line)) continue;
        if (line.length <= 20) { teacher = line; break; }
      }
    }
    if (!room) {
      for (var j = 0; j < ls.length; j++) {
        var line2 = ls[j];
        if (line2 === name || line2 === teacher) continue;
        if (/[A-Za-z]?\d{1,3}\s*[-－]\s*\d{2,4}|[A-Za-z]\d{1,3}|楼|室|教室|校区/.test(line2)) {
          room = line2;
          break;
        }
      }
    }
    return make(name, teacher, room, weekDay, start, end, weekSpec);
  }

  function sectionForRow(row, fallback) {
    if (!row || !row.cells || !row.cells.length) return fallback;
    var head = text(row.cells[0]);
    var sec = sections(head);
    if (sec) return sec[0];
    var idx = row.rowIndex + 1;
    return Math.max(1, fallback || idx);
  }

  function scanList(table) {
    var rows = table.rows;
    if (!rows || rows.length < 2) return [];
    var header = -1;
    var headerTexts = null;
    for (var r = 0; r < Math.min(4, rows.length); r++) {
      var cells = rows[r].cells;
      var texts = [];
      var hits = 0;
      for (var c = 0; c < cells.length; c++) {
        var t = text(cells[c]);
        texts.push(t);
        if (/课程|科目/.test(t)) hits++;
        if (/星期|周几|节次|周次/.test(t)) hits++;
      }
      if (hits >= 2) { header = r; headerTexts = texts; break; }
    }
    if (header < 0 || !headerTexts) return [];
    function index(keys, exclude) {
      for (var i = 0; i < headerTexts.length; i++) {
        var t = headerTexts[i];
        for (var k = 0; k < keys.length; k++) {
          if (t.indexOf(keys[k]) >= 0 && (!exclude || !exclude.test(t))) return i;
        }
      }
      return -1;
    }
    var nameIdx = index(['课程名称', '课程名', '科目名称', '课程'], /性质|代码|编号|学分|节次|周次/);
    var weekdayIdx = index(['星期', '周几', '上课星期'], null);
    var sectionIdx = index(['节次', '上课节次', '节'], /周/);
    var weeksIdx = index(['周次', '起止周', '上课周'], null);
    var teacherIdx = index(['教师', '老师', '任课教师'], null);
    var roomIdx = index(['地点', '教室', '上课地点'], null);
    if (nameIdx < 0 || weekdayIdx < 0 || sectionIdx < 0) return [];
    var out = [];
    for (var r2 = header + 1; r2 < rows.length; r2++) {
      var cs = rows[r2].cells;
      if (!cs || cs.length <= nameIdx) continue;
      var name = text(cs[nameIdx]);
      var wd = weekday(text(cs[weekdayIdx]));
      var sec = sections(text(cs[sectionIdx]));
      if (!name || !wd || !sec) continue;
      var wk = weeksIdx >= 0 && cs[weeksIdx] ? normalizeWeeks(text(cs[weeksIdx])) : '1-16';
      var teacher = teacherIdx >= 0 && cs[teacherIdx] ? text(cs[teacherIdx]) : '';
      var room = roomIdx >= 0 && cs[roomIdx] ? text(cs[roomIdx]) : '';
      var item = make(name, teacher, room, wd, sec[0], sec[1], wk || '1-16');
      if (item) out.push(item);
    }
    return out;
  }

  function scanGrid(table) {
    var rows = table.rows;
    if (!rows || rows.length < 2) return [];
    var header = -1;
    var map = {};
    for (var r = 0; r < Math.min(4, rows.length); r++) {
      var map2 = {};
      var count = 0;
      var cells = rows[r].cells;
      for (var c = 0; c < cells.length; c++) {
        var wd = weekday(text(cells[c]));
        if (wd) { map2[c] = wd; count++; }
      }
      if (count >= 3) { header = r; map = map2; break; }
    }
    if (header < 0) return [];
    var out = [];
    var occupied = {};
    for (var r2 = header + 1; r2 < rows.length; r2++) {
      var cells2 = rows[r2].cells;
      var col = 0;
      for (var k = 0; k < cells2.length; k++) {
        while (occupied[r2] && occupied[r2][col]) col++;
        var cell = cells2[k];
        var rowspan = cell.rowSpan || 1;
        var colspan = cell.colSpan || 1;
        if (map[col]) {
          var start = sectionForRow(rows[r2], r2 - header);
          var end = start + rowspan - 1;
          var blocks = cell.querySelectorAll('.timetable_con,.kbcontent,[class*=timetable_con],[class*=course-item]');
          if (!blocks.length) blocks = [cell];
          for (var b = 0; b < blocks.length; b++) {
            var item = courseFrom(blocks[b], map[col], start, end);
            if (item) out.push(item);
          }
        }
        for (var rr = r2; rr < r2 + rowspan; rr++) {
          if (!occupied[rr]) occupied[rr] = {};
          for (var cc = col; cc < col + colspan; cc++) occupied[rr][cc] = true;
        }
        col += colspan;
      }
    }
    return out;
  }

  function scanAll() {
    var found = [];
    var all = docs();
    for (var d = 0; d < all.length; d++) {
      var tables = all[d].querySelectorAll('table');
      for (var t = 0; t < tables.length; t++) {
        var list = scanList(tables[t]);
        if (list.length) {
          for (var a = 0; a < list.length; a++) found.push(list[a]);
          continue;
        }
        var grid = scanGrid(tables[t]);
        if (grid.length) {
          for (var g = 0; g < grid.length; g++) found.push(grid[g]);
        }
      }
    }
    var unique = [];
    var seen = {};
    for (var i = 0; i < found.length && unique.length < 120; i++) {
      var item = found[i];
      var key = item.name + '|' + item.weekday + '|' + item.start + '|' + item.end + '|' + item.weeks + '|' + item.room;
      if (seen[key]) continue;
      seen[key] = true;
      unique.push(item);
    }
    return unique;
  }

  window.__fosuScan = function (manual) {
    try {
      var result = scanAll();
      if (result.length) {
        if (window.FosuBridge && FosuBridge.onCourses) {
          FosuBridge.onCourses(JSON.stringify(result));
        }
        return 'ok:' + result.length;
      }
      if (manual && window.FosuBridge && FosuBridge.onScanEmpty) {
        FosuBridge.onScanEmpty();
      }
      return 'empty';
    } catch (e) {
      if (manual && window.FosuBridge && FosuBridge.onScanEmpty) {
        FosuBridge.onScanEmpty();
      }
      return 'error';
    }
  };

  window.__fosuOpenTimetable = function () {
    var prefs = ['学生课表', '我的课表', '个人课表', '课表查询', '课表'];
    var all = docs();
    for (var p = 0; p < prefs.length; p++) {
      for (var d = 0; d < all.length; d++) {
        var elements = all[d].querySelectorAll('a,button,li,span');
        for (var i = 0; i < elements.length; i++) {
          var el = elements[i];
          var t = text(el).replace(/\s/g, '');
          if (!t || t.length > prefs[p].length + 10) continue;
          if (t.indexOf(prefs[p]) < 0) continue;
          if (!visible(el)) continue;
          var target = el.tagName === 'A' ? el : (el.querySelector('a') || el);
          try {
            target.click();
            if (window.FosuBridge && FosuBridge.onNavigateResult) {
              FosuBridge.onNavigateResult('clicked:' + prefs[p]);
            }
            return 'clicked:' + prefs[p];
          } catch (e) {
          }
        }
      }
    }
    if (window.FosuBridge && FosuBridge.onNavigateResult) {
      FosuBridge.onNavigateResult('notfound');
    }
    return 'notfound';
  };
})();
