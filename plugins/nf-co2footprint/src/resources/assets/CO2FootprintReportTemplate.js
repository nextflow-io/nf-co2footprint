// JavaScript used to power the Nextflow Report Template output.
window.data_byprocess = {};

/* helper functions that takes an array of numbers 
    units are in milliwatt-hours (mWh) or milligramm (mg) and are converted to its base unit */
function norm_units( list ) {
  if( list == null ) return null;
  var result = new Array(list.length);
  for( i=0; i<list.length; i++ ) {
    var value = list[i];
    result[i] = value / 1000;
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
  var data = [];
  for(var pname in window.data_byprocess){
    if( !window.data_byprocess.hasOwnProperty(pname) )
        continue;
    var smry = window.data_byprocess[pname];
    data.push({x:pname, y: norm_units(smry.co2e), name: pname, legendgroup: pname, type:'box', boxmean: true, boxpoints: false});
    // energy will be plotted with transparent color, hiding hover info and legend, but linked to tye right y-axis
    data.push({x:pname, y: norm_units(smry.energy), name: pname, legendgroup: pname, type:'box', boxmean: true, boxpoints: false, yaxis: 'y2', showlegend:false, hoverinfo: 'skip', marker: {color: 'rgba(0,0,0,0)'}, fillcolor: 'rgba(0,0,0,0)'});
  }
  
  var tickformat = [{
    "dtickrange": [null, 4],
    "value": ".2f"
  },
  {
    "dtickrange": [4, null],
    "value": ".3s"
  }];

  var layout = {
    title: 'CO<sub>2</sub> emission & energy consumption',
    legend: {x: 1.1},
    xaxis: {domain: [0.2, 1]},
    yaxis: {title: 'CO2e emission (g)',
            rangemode: 'tozero',
            tickformatstops: tickformat
    },
    yaxis2: {title: 'Energy consumption (Wh)',
            rangemode: 'tozero',
            gridcolor: 'rgba(0, 0, 0, 0)', // transparent grid lines
            overlaying: 'y',
            side: 'right',
            tickformatstops: tickformat
    },
  };
  
  Plotly.newPlot('co2eplot', data, layout);

  // Convert to readable units
  function readable_units(value, unit_index) {
    units = ['p', 'n', 'u', 'm', ' ', 'K', 'M', 'G', 'T', 'P', 'E']  // Units: pico, nano, micro, mili, 0, Kilo, Mega, Giga, Tera, Peta, Exa
    
    while (value >= 1000 && unit_index < units.length - 1) {
        value /= 1000;
        unit_index++;
    }
    while (value <= 1 && unit_index > 0) {
        value *= 1000;
        unit_index--;
    }
    
    value = Math.round( value * 100 ) / 100;
    return value + ' ' + units[unit_index];
  }
  
  // Convert miliseconds to readable units
  function readable_units_time(duration){
    if (duration < 1000) {
      return duration + "ms"
    } else {
      hours = Math.floor(duration / 3600000);
      minutes = Math.floor((duration % 3600000) / 60000);
      seconds = Math.floor(duration % 60000) / 1000;

      if (duration < 60000) {
        return seconds + "s";
      } else if (duration < 3600000) {
        return minutes + "m " + seconds + "s";
      } else {
        return hours + "h " + minutes + "m " + seconds + "s";
      }
    }
  }

  // Convert bytes to readable units
  function readable_units_memory(bytes){
    units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB']  // Units: Byte, Kilobyte, Megabyte, Gigabyte, Terabyte, Petabyte, Exabyte
    unit_index=0

    while (bytes >= 1024 && unit_index < units.length - 1) {
      bytes /= 1024;
      unit_index++;
    }
    
    return bytes + ' ' + units[unit_index];
  }  

  // Build the trace table
  function make_co2e(mg, type){
    if (type === 'sort') {
      return parseFloat(mg);
    }
    if($('#nf-table-humanreadable').val() == 'false'){
      return mg;
    }
    if (mg == '-' || mg == 0){
      return mg;
    }
    return readable_units(mg, 3) + 'g';
  }
  function make_energy(mWh, type){
    if (type === 'sort') {
      return parseFloat(mWh);
    }
    if($('#nf-table-humanreadable').val() == 'false'){
      return mWh;
    }
    if (mWh == '-' || mWh == 0){
      return mWh;
    }
    return readable_units(mWh, 3) + 'Wh';
  }
  function make_time(ms, type){
    if (type === 'sort') {
      return parseInt(ms);
    }
    if($('#nf-table-humanreadable').val() == 'false'){
      return ms;
    }
    if (ms == '-' || ms == 0){
      return ms;
    }
    return readable_units_time(ms);
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
    return readable_units_memory(bytes);
  }
  function make_core_usage_factor(uf, type){
      if (type === 'sort') {
        return parseFloat(uf);
      }
      if($('#nf-table-humanreadable').val() == 'false'){
        return uf;
      }
      if (uf == '-' || uf == 0){
        return uf;
      }
      return Math.round( uf * 1000 ) / 1000;
    }
  function make_tasks_table(){
    // reset
      if ( $.fn.dataTable.isDataTable( '#tasks_table' ) ) {
        $('#tasks_table').DataTable().destroy();
      }

      // Column titles
      var energyConsumptionTitle = 'energy consumption (mWh)'; // Default column title
      var co2EmissionsTitle = 'CO2e emissions (mg)';
      if ($('#nf-table-humanreadable').val() == 'true') {
        energyConsumptionTitle = 'energy consumption'; // Change the column title if the button is selected
        co2EmissionsTitle = 'CO2e emissions';
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
          { title: co2EmissionsTitle, data: 'co2e', type: 'num', render: make_co2e },
          { title: energyConsumptionTitle, data: 'energy', type: 'num', render: make_energy },
          { title: 'Time', data: 'time', type: 'num', render: make_time },
          { title: 'Number of cores', data: 'cpus', type: 'num' },
          { title: 'Power draw of a computing core', data: 'powerdrawCPU', type: 'num'},
          { title: 'Core usage factor', data: 'cpuUsage', type: 'num', render: make_core_usage_factor },
          { title: 'Memory', data: 'memory', type: 'num', render: make_memory },
          { title: 'CPU model', data: 'cpu_model' },
        ],
        "deferRender": true,
        "lengthMenu": [[25, 50, 100, -1], [25, 50, 100, "All"]],
        "scrollX": true,
        "colReorder": true,
        "columnDefs": [
          { className: "id", "targets": [ 0,1,2,3 ] },
          { className: "meta", "targets": [ 4,7,8,9,10,11 ] },
          { className: "metrics", "targets": [ 5,6 ] }
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

  // Create options table
  function make_options_table(){
    // reset
    if ( $.fn.dataTable.isDataTable( '#options_table' ) ) {
      $('#options_table').DataTable().destroy();
    }

    var table = $('#options_table').DataTable({
      data: window.options,
      columns: [
          { title: "Option", data: "option" },
          { title: "Value", data: "value" }
        ],
        "deferRender": true,
        "lengthMenu": [[25, 50, 100, -1], [25, 50, 100, "All"]],
    });
  }

  // Make the table on page load
  make_options_table();
});