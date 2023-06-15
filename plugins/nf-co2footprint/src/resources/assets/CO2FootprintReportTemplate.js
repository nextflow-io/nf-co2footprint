// JavaScript used to power the Nextflow Report Template output.
window.data_byprocess = {};

/* helper functions that takes an array of numbers each of each
   is a integer representing a number of bytes and normalise to base 2 scale */
function norm_mem( list ) {
  if( list == null ) return null;
  var result = new Array(list.length);
  for( i=0; i<list.length; i++ ) {
    var value = list[i];
    var x = Math.floor(Math.log10(value) / Math.log10(1024));
    if( x == 0 )
      value = value/1.024;
    else {
      for( j=0; j<x; j++ )
        value = value / 1.024;
    }
    result[i] = Math.round(value);
  }
  return result;
}

$(function() {
  // Script block clicked
  $('#tasks_table').on('click', '.script_block', function(e){
    e.preventDefault();
    $(this).toggleClass('short');
  });

  $(function() {
    $('[data-toggle="tooltip"]').tooltip()
  })

  // Completed date from now
  var completed_date = moment( $('#workflow_complete').text(), "ddd MMM DD HH:mm:ss .* YYYY" );
  if(completed_date.isValid()){
    $('#completed_fromnow').html('completed ' + completed_date.fromNow() + ', ');
  }

  // Collect metrics by process
  for(let i in window.data.summary){
    let metrics = window.data.summary[i];
    let proc = metrics.process;
    
    if(!window.data_byprocess.hasOwnProperty(proc)){
      window.data_byprocess[proc] = {};
    }

    for (let key in metrics) {
      if (metrics[key] != null) {
        window.data_byprocess[proc][key] = [];
        if( metrics[key].min == metrics[key].max ) {
            // min equals max ==> show just a value
            window.data_byprocess[proc][key].push(metrics[key].min);
        }
        else {
            // otherwise show all values
            window.data_byprocess[proc][key].push(metrics[key].min);
            window.data_byprocess[proc][key].push(metrics[key].q1);
            window.data_byprocess[proc][key].push(metrics[key].q1);
            window.data_byprocess[proc][key].push(metrics[key].q2);
            window.data_byprocess[proc][key].push(metrics[key].q3);
            window.data_byprocess[proc][key].push(metrics[key].q3);
            window.data_byprocess[proc][key].push(metrics[key].max);
        }
        if (key == "time") {
            window.data_byprocess[proc][key] = window.data_byprocess[proc][key].map(function(d,i){
            return moment.duration(d).asMinutes().toFixed(1);
          });
        }
      }
    }
  }

  // Plot histograms of resource usage
  var co2e_data = [];
  var energy_data = [];
  for(var pname in window.data_byprocess){
    if( !window.data_byprocess.hasOwnProperty(pname) )
        continue;
    var smry = window.data_byprocess[pname];
    co2e_data.push({y: smry.co2e, name: pname, type:'box', boxmean: true, boxpoints: false});
    energy_data.push({y: smry.energy, name: pname, type:'box', boxmean: true, boxpoints: false});

  }

  Plotly.newPlot('co2eplot', co2e_data, { title: 'CO2 emission', yaxis: {title: 'CO2 emission (g)', tickformat: '.1f', rangemode: 'tozero'} });
  Plotly.newPlot('energyplot', energy_data, { title: 'Energy consumption', yaxis: {title: 'Energy consumption (Wh)', tickformat: '.1f', rangemode: 'tozero'} });
  
  // Only plot tabbed plots when shown
  $('#pctco2eplot_tablink').on('shown.bs.tab', function (e) {
    if($('#pctco2eplot').is(':empty')){
      Plotly.newPlot('pctco2eplot', co2e_data, { title: '% ?', yaxis: {title: '% ?', tickformat: '.1f', rangemode: 'tozero'} });
    }
  });
  $('#pctenergyplot_tablink').on('shown.bs.tab', function (e) {
    if($('#pctenergyplot').is(':empty')){
      Plotly.newPlot('pctenergyplot', energy_data, { title: '% ?', yaxis: {title: '% ?', tickformat: '.1f', rangemode: 'tozero'} });
    }
  });

  // Humanize duration
  function humanize(duration){
    if (duration.days() > 0) {
      return duration.days() + "d " + duration.hours() + "h"
    }
    if (duration.hours() > 0) {
      return duration.hours() + "h " + duration.minutes() + "m"
    }
    if (duration.minutes() > 0) {
      return duration.minutes() + "m " + duration.seconds() + "s"
    }
    return duration.asSeconds().toFixed(1) + "s"
  }

  // Build the trace table
  function make_duration(ms, type){
    if (type === 'sort') {
      return parseInt(ms);
    }
    if($('#nf-table-humanreadable').val() == 'false'){
      return ms;
    }
    if (ms == '-' || ms == 0){
      return ms;
    }
    return humanize(moment.duration( parseInt(ms) ));
  }
  function make_date(ms, type){
    if (type === 'sort') {
      return parseInt(ms);
    }
    if($('#nf-table-humanreadable').val() == 'false'){
      return ms;
    }
    if (ms == '-' || ms == 0){
      return ms;
    }
    return moment( parseInt(ms) ).format();
  }
  function make_memory(bytes, type){
    if (type === 'sort') {
      return parseInt(bytes);
    }
    if($('#nf-table-humanreadable').val() == 'false'){
      return bytes;
    }
    if (bytes == '-' || bytes == 0){
      return bytes;
    }
    // https://stackoverflow.com/a/14919494
    var thresh = 1024;
    if(Math.abs(bytes) < thresh) {
      return bytes + ' B';
    }
    var units = ['kB','MB','GB','TB','PB','EB','ZB','YB'];
    var u = -1;
    do {
        bytes /= thresh;
        ++u;
    } while(Math.abs(bytes) >= thresh && u < units.length - 1);
    return bytes.toFixed(3)+' '+units[u];
  }
  function make_tasks_table(){
    // reset
      if ( $.fn.dataTable.isDataTable( '#tasks_table' ) ) {
        $('#tasks_table').DataTable().destroy();
      }
      var table = $('#tasks_table').DataTable({
        data: window.data.trace,
        columns: [
          { title: 'task_id', data: 'task_id' },
          { title: 'process', data: 'process' },
          { title: 'tag', data: 'tag' },
          { title: 'status', data: 'status', render: function(data, type, row){
              var s = {
                COMPLETED: 'success',
                CACHED: 'secondary',
                ABORTED: 'danger',
                FAILED: 'danger'
              }
              return '<span class="badge badge-'+s[data]+'">'+data+'</span>';
            }
          },
          { title: 'hash', data: 'hash', render:  function(data, type, row){
              var script = '';
              var lines = data.split("\n");
              var ws_re = /^(\s+)/g;
              var flws_match = ws_re.exec(lines[1]);
              if(flws_match == null){
                script = data;
              } else {
                for(var j=0; j<lines.length; j++){
                  script += lines[j].replace(new RegExp('^'+flws_match[1]), '').replace(/\s+$/,'') + "\n";
                }
              }
              return '<code>'+script+'</code>';
            }
          },
          { title: 'allocated cpus', data: 'cpus' },
          { title: '%cpu', data: '%cpu' },
          { title: 'allocated memory', data: 'memory', type: 'num', render: make_memory },
          { title: '%mem', data: '%mem' },
          { title: 'vmem', data: 'vmem', type: 'num', render: make_memory },
          { title: 'rss', data: 'rss', type: 'num', render: make_memory },
          { title: 'peak_vmem', data: 'peak_vmem', type: 'num', render: make_memory },
          { title: 'peak_rss', data: 'peak_rss', type: 'num', render: make_memory },
          { title: 'allocated time', data: 'time', type: 'num', render: make_duration },
          { title: 'duration', data: 'duration', type: 'num', render: make_duration },
          { title: 'realtime', data: 'realtime', type: 'num', render: make_duration },
          { title: 'script', data: 'script', render: function(data) {
              return '<pre class="script_block short"><code>' + data.trim() + '</code></pre>';
            }
          },
          { title: 'exit', data: 'exit' },
          { title: 'submit', data: 'submit', type: 'num', render: make_date },
          { title: 'start', data: 'start', type: 'num', render: make_date },
          { title: 'complete', data: 'complete', type: 'num', render: make_date },
          { title: 'rchar', data: 'rchar', type: 'num', render: make_memory },
          { title: 'wchar', data: 'wchar', type: 'num', render: make_memory },
          { title: 'syscr', data: 'syscr', type: 'num', render: make_memory },
          { title: 'syscw', data: 'syscw', type: 'num', render: make_memory },
          { title: 'read_bytes', data: 'read_bytes', type: 'num', render: make_memory },
          { title: 'write_bytes', data: 'write_bytes', type: 'num', render: make_memory },
          { title: 'native_id', data: 'native_id' },
          { title: 'name', data: 'name' },
          { title: 'module', data: 'module' },
          { title: 'container', data: 'container', render: function(data) {
              return '<samp>'+data+'</samp>';
            }
          },
          { title: 'disk', data: 'disk' },
          { title: 'attempt', data: 'attempt' },
          { title: 'scratch', data: 'scratch', render: function(data) {
              return '<samp>'+data+'</samp>';
            }
          },
          { title: 'workdir', data: 'workdir', render: function(data) {
              return '<samp>'+data+'</samp>';
            }
          }
        ],
        "deferRender": true,
        "lengthMenu": [[25, 50, 100, -1], [25, 50, 100, "All"]],
        "scrollX": true,
        "colReorder": true,
        "columnDefs": [
          { className: "id", "targets": [ 0,1,2,3 ] },
          { className: "meta", "targets": [ 4,13,16,17,18,19,20,27,28,29,30,31,32,33,34 ] },
          { className: "metrics", "targets": [ 5,6,7,8,9,10,11,12,14,15,21,22,23,24,25,26 ] }
        ],
        "buttons": [
          {
            extend: 'colvisGroup',
            text: 'Metrics',
            show: [ '.id', '.metrics' ],
            hide: [ '.meta' ],
          },
          {
            extend: 'colvisGroup',
            text: 'Metadata',
            show: [ '.id', '.meta'],
            hide: [ '.metrics' ],
          },
          {
            extend: 'colvisGroup',
            text: 'All',
            show: ':hidden',
          },
        ]
      });

      // Insert column filter button group
      table.buttons().container()
         .prependTo( $('#tasks_table_filter') );

      // Column filter button group onClick event to highlight active filter
      $('.buttons-colvisGroup').click(function(){
        var def = 'btn-secondary';
        var sel = 'btn-primary';
        $('.buttons-colvisGroup').removeClass(sel).addClass(def);
        $(this).addClass(sel).removeClass(def);
      });

      // Default filter highlight
      $(".buttons-colvisGroup:contains('All')").click();
    }

  if( window.data.trace==null ) {
      // nascondere
      $('#table-container').remove()
  }
  else {
      $('#no-table-container').remove()
      // Dropdown changed about raw / human readable values in table
      $('#nf-table-humanreadable').change(function(){
        make_tasks_table();
      });
      // Make the table on page load
      make_tasks_table();
  }


});
