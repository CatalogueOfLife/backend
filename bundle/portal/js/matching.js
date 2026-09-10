/*
 * Bulk name matching against the single release this bundle serves.
 *
 * The bundle mounts FixedNameUsageMatchingResource at the keyless /match/nameusage.
 * Three things about that endpoint drive this page:
 *
 *   - it is NOT multipart. The body must be the raw file bytes with a text/* content
 *     type; a FormData post is answered with 415.
 *   - it needs no credentials, which is why a bundle uses it rather than the
 *     job based endpoint the full ChecklistBank offers.
 *   - the response is a ZIP holding one matched table, but it is labelled
 *     text/plain. We therefore name the download .zip ourselves rather than
 *     trusting the content type.
 */
(function () {
  'use strict';

  var b = window.colBundle;

  var drop = document.getElementById('match-drop');
  var input = document.getElementById('match-input');
  var run = document.getElementById('match-run');
  var status = document.getElementById('match-status');
  var format = document.getElementById('match-format');
  var filename = document.getElementById('match-filename');

  var file = null;
  var busy = false;

  function say(html, cls) {
    status.innerHTML = html ? '<div class="' + (cls || '') + '">' + html + '</div>' : '';
  }

  // Round kB hides a small result entirely - a few hundred matched rows is under 1 kB zipped.
  function size(bytes) {
    if (bytes < 1024) return bytes.toLocaleString() + ' bytes';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' kB';
    return (bytes / 1024 / 1024).toFixed(1) + ' MB';
  }

  function contentType(name) {
    if (/\.tsv$/i.test(name)) return 'text/tab-separated-values';
    if (/\.csv$/i.test(name)) return 'text/csv';
    return 'text/plain';
  }

  /* The job throws without a scientificName column, so catch it here where we can
     say something useful, rather than after uploading the whole file. */
  function checkHeader(f) {
    return f.slice(0, 8192).text().then(function (head) {
      var line = head.split(/\r?\n/)[0] || '';
      var cols = line.split(/[\t,;|]/).map(function (c) {
        return c.replace(/^['"\s]+|['"\s]+$/g, '').toLowerCase();
      });
      if (cols.indexOf('scientificname') === -1) {
        throw new Error('No scientificName column in the header row. Found: ' +
          (cols.filter(Boolean).join(', ') || '(nothing)'));
      }
      return cols;
    });
  }

  function choose(f) {
    file = null;
    run.disabled = true;
    if (!f) return;

    filename.textContent = f.name;
    drop.classList.add('has-file');
    say('Checking the header row…');

    checkHeader(f).then(function (cols) {
      file = f;
      run.disabled = false;
      say('Ready: <strong>' + b.escape(f.name) + '</strong> (' + cols.filter(Boolean).length +
          ' columns). Press <em>Match names</em>.');
    }).catch(function (e) {
      drop.classList.remove('has-file');
      say(b.escape(e.message), 'bundle-error');
    });
  }

  drop.addEventListener('click', function () { if (!busy) input.click(); });
  input.addEventListener('change', function () { choose(input.files[0]); });

  ['dragenter', 'dragover'].forEach(function (ev) {
    drop.addEventListener(ev, function (e) { e.preventDefault(); drop.classList.add('dragover'); });
  });
  ['dragleave', 'drop'].forEach(function (ev) {
    drop.addEventListener(ev, function (e) { e.preventDefault(); drop.classList.remove('dragover'); });
  });
  drop.addEventListener('drop', function (e) {
    if (busy) return;
    var f = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0];
    if (f) choose(f);
  });

  run.addEventListener('click', function () {
    if (!file || busy) return;
    busy = true;
    run.disabled = true;
    // One long lived response with no progress events - the endpoint streams the
    // whole file through the matcher before the body completes.
    say('Matching <strong>' + b.escape(file.name) + '</strong>… this holds one request open ' +
        'until every row is done, so keep this tab open.');

    fetch(b.api + 'match/nameusage?format=' + encodeURIComponent(format.value), {
      method: 'POST',
      headers: { 'Content-Type': contentType(file.name) },
      body: file
    }).then(function (r) {
      if (!r.ok) {
        return r.text().then(function (t) {
          throw new Error('The server answered ' + r.status + '. ' + t.slice(0, 400));
        });
      }
      return r.blob();
    }).then(function (blob) {
      // Labelled text/plain but actually a ZIP, so name it accordingly.
      var base = file.name.replace(/\.[^.]+$/, '');
      var url = URL.createObjectURL(blob);
      var a = document.createElement('a');
      a.href = url;
      a.download = 'match-' + base + '.zip';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      setTimeout(function () { URL.revokeObjectURL(url); }, 60000);

      say('Done. <strong>match-' + b.escape(base) + '.zip</strong> was downloaded ' +
          '(' + size(blob.size) + '). ' +
          'It holds one ' + b.escape(format.value.toLowerCase()) + ' table with your original ' +
          'columns prefixed <code>original_</code> beside the match.');
    }).catch(function (e) {
      say('Matching failed: ' + b.escape(e.message), 'bundle-error');
    }).then(function () {
      busy = false;
      run.disabled = !file;
    });
  });
})();
