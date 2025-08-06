// JavaScript used to power the Nextflow Report Template output.

//
// Converter methods
//

/**
 * Converts a list of numbers in milli-units (mWh, mg,...) to base units (Wh, g)
 * @param {ArrayLike} list An array with numbers
 * @returns The array, with each value divided by 1000
 */
function norm_units( list ) {
  return list?.map(v => v / 1000) ?? null;
}

/**
 * Convert to readable units, optionally with a starting scope and a unit label
 *
 * @param {*} value The value to be converted
 * @param {*} scope The current scope of the value
 * @param {*} unit The unit of the value
 * @returns Decimally shifted value
 */
function toReadableUnits(value, scope = '', unit = '') {
  var scopes = ['p', 'n', 'u', 'm', '', 'K', 'M', 'G', 'T', 'P', 'E']; // Units: pico, nano, micro, milli, 0, Kilo, Mega, Giga, Tera, Peta, Exa
  var scopeIndex = scopes.indexOf(scope);

  while (value >= 1000 && scopeIndex < scopes.length - 1) {
    value /= 1000;
    scopeIndex++;
  }
  while (value <= 1 && scopeIndex > 0) {
    value *= 1000;
    scopeIndex--;
  }

  value = Math.round(value * 100) / 100;
  return value + ' ' + scopes[scopeIndex] + unit;
}


/**
 * Convert bytes to readable units, starting from a specified unit.
 *
 * @param {*} value The value to be converted (e.g., 1, 1024, etc.)
 * @param {string} currentUnit The unit of the input value (default: 'B')
 * @returns Bytes in a readable scope
 */
function toReadableByteUnits(value, currentUnit = 'B') {
  var units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB']; // Units: Byte, Kilobyte, Megabyte, etc.
  var unit_index = units.indexOf(currentUnit);

  while (value >= 1024 && unit_index < units.length - 1) {
    value /= 1024;
    unit_index++;
  }

  return value + ' ' + units[unit_index];
}

/**
 * Converts the given time to another unit of time
 *
 * @param {*} value The time as a number in original given unit
 * @param {*} unit Given unit of time
 * @param {*} targetUnit Unit of time to be converted to
 * @return Number of converted time
 */
function convertTime(value, unit='ms', targetUnit='s') {
    var units = ['ns', 'mus', 'ms', 's', 'min', 'h', 'days', 'weeks', 'months', 'years']   // Units of time
    var steps = [1000.0, 1000.0, 1000.0, 60.0, 60.0, 24.0, 7.0, 4.35, 12.0]                // (Average) magnitude change between units

    var givenUnitPos = units.indexOf(unit)
    var targetUnitPos = units.indexOf(targetUnit)


    // Obtain conversion rates in the given range
    if (targetUnitPos > givenUnitPos) {
      steps.slice(givenUnitPos, targetUnitPos).forEach( function(it) { value /= it} )
    }
    else if (targetUnitPos < givenUnitPos) {
      steps.slice(targetUnitPos, givenUnitPos).forEach( function(it) { value *= it} )
    }

    return value
}

/**
 * Converts a time value to a human-readable string
 *
 * @param {*} value The time as a number in original given unit
 * @param {*} unit Given unit of time
 * @param {*} smallestUnit The smallest unit to convert to
 * @param {*} largestUnit The largest unit to convert to
 * @param {*} threshold The minimum value for the conversion to be included in the output
 * @return A human-readable string representation of the time value
 */
function toReadableTimeUnits(value, unit = 'ms', smallestUnit = 's', largestUnit = 'years', threshold = null) {
  const units = ['ns', 'mus', 'ms', 's', 'min', 'h', 'days', 'weeks', 'months', 'years'];
  const steps = [1000, 1000, 1000, 60, 60, 24, 7, 4.35, 12];

  const smallestIdx = units.indexOf(smallestUnit);
  const largestIdx = units.indexOf(largestUnit);
  if (smallestIdx === -1 || largestIdx === -1) throw new Error("Unknown unit");

  let remaining = convertTime(value, unit, units[largestIdx]);
  let parts = [];

  for (let i = largestIdx; i >= smallestIdx; i--) {
    let unitValue;
    if (i > smallestIdx) {
      unitValue = Math.floor(remaining);
      remaining = (remaining - unitValue) * steps[i - 1];
    } else {
      // For the smallest unit, keep up to 2 decimals
      unitValue = Math.round(remaining * 100) / 100;
      remaining = 0;
    }

    if ((threshold === null && unitValue > 0) || (threshold !== null && unitValue > threshold)) {
      let label = (unitValue === 1 && ['days', 'weeks', 'months', 'years'].includes(units[i]))
        ? units[i].slice(0, -1)
        : units[i];
      let formatted = parseFloat(unitValue).toString();
      parts.push(`${formatted}${label}`);
    }
  }

  // Carry-over: if the last unit is exactly the conversion factor, increment previous unit
  if (parts.length && smallestIdx > 0) {
    let lastPart = parts[parts.length - 1];
    let lastValue = parseFloat(lastPart.replace(/[^\d.]/g, ''));
    let lastUnit = lastPart.replace(/[\d.]/g, '');
    let conversionFactor = steps[smallestIdx - 1];
    if (lastValue === conversionFactor) {
      parts.pop();
      let prevPart = parts[parts.length - 1];
      let prevNum = parseFloat(prevPart.replace(/[^\d.]/g, ''));
      let prevUnit = prevPart.replace(/[\d.]/g, '');
      let newPrev = prevNum + 1;
      parts[parts.length - 1] = `${parseFloat(newPrev).toString()}${prevUnit}`;
    }
  }

  return parts.length ? parts.join(' ') : `0${smallestUnit}`;
}

//
// Render functions
//

/**
 * Perform checks on the data before using other methods to process
 *
 * @param {*} data The data (number/string/...)
 * @param {*} type Type of the data
 * @param {function} parseFunction Function to parse the data
 * @returns
 */
function check_data(data, type, parseFunction) {
  if (type === 'sort') {
    return parseFunction(data);
  }
  if($('#nf-table-humanreadable').val() == 'false'){
    return data;
  }
  if (data == '-' || data == 0){
    return data;
  }
  return null
}

/**
 * Render CO2 equivalents in desired output format
 *
 * @param {*} mg
 * @param {*} type Type of the data
 * @returns CO2 equivalents as a readable unit
 */
function make_co2e(mg, type){
  return toReadableUnits(mg, 'm', 'g');
}

/**
 * Render energy in desired output format
 *
 * @param {*} mWh Energy in milli-Watt-hours
 * @param {*} type Type of the data
 * @returns Energy as a readable unit
 */
function make_energy(mWh, type){
  return check_data(mWh, type, parseFloat) ?? toReadableUnits(mWh, 'm', 'Wh');
}

/**
 * Render time in desired output format
 *
 * @param {*} h Time in hours
 * @param {*} type Type of the data
 * @returns The readable time
 */
function make_time(h, type){
  return check_data(data, type, parseFloat) ?? toReadableTimeUnits(parseFloat(h), 'h', 'ms', 'days', 0.0);
}

/**
 * Render carbon intensity in desired output format
 *
 * @param {*} ci Carbon intensity value
 * @param {*} type Type of the data
 * @returns Carbon intensity as a readable unit
 */
function make_carbon_intensity(ci, type) {
  return check_data(data, type, parseFloat) ?? toReadableUnits(ci, 'm', 'gCO<sub>2</sub>e/kWh');
}

/**
 * Render memory size in desired output format
 *
 * @param {*} value Memory value to be rendered
 * @param {*} type Type of the data
 * @param {string} unit The unit of the input value (default: 'B')
 * @returns The memory at a readable scale
 */
function make_memory(value, type) {
  return check_data(value, type, parseFloat) ?? toReadableByteUnits(value, 'GB');
}

/**
 * Render the per core power draw in desired output format
 *
 * @param {*} powerDrawCPU Power draw of the CPU in Watts
 * @param {*} type Type of the data
 * @returns Readable power draw in Watts
 */
function make_power_draw_cpu(powerDrawCPU, type) {
  return check_data(powerDrawCPU, type, parseFloat) ?? `${powerDrawCPU}%`;
}

/**
 * Render usage factor in desired output format
 *
 * @param {*} usageFactor Usage factor of CPU in percent
 * @param {*} type Type of the data
 * @returns Readable usage factor
 */
function make_core_usage_factor(usageFactor, type){
  return check_data(usageFactor, type, parseFloat) ?? `${usageFactor} W`;
}

// Map for collecting statistics by process
window.statsByProcess = {};

//
// This block is only executed after the page is fully loaded
//
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
  for(let processName in window.data.summary){
    let metrics = window.data.summary[processName];

    // Add an empty map if the process is not already present
    window.statsByProcess[processName] ??= {};

    for (let metricName in metrics) {
      // Skip if metric is not present
      if (metrics[metricName] == null) { continue; }

      if( metrics[metricName]['min'] == metrics[metricName]['max'] ) {
        // min equals max ==> show just a value
        window.statsByProcess[processName][metricName] = [ metrics[metricName]['min'] ];
      }
      else {
          // otherwise show all values
          window.statsByProcess[processName][metricName] = ['min', 'q1', 'q2', 'q3', 'max'].map(stat => metrics[metricName][stat])
      }
      if (metricName == "time") {
        window.statsByProcess[processName][metricName] = window.statsByProcess[processName][metricName].map(function(d,i){
          return moment.duration(d).asMinutes().toFixed(1);
        });
      }
    }
  }

  // Plot histograms of resource usage
  var plot_data_total = [];
  var plot_data_non_cached = [];
  for(var processName in window.statsByProcess){

    // Extract process statistics
    var stats = window.statsByProcess[processName];

    // Add CO2 Boxplot to plot
    plot_data_total.push(
      {
        x:processName, y: norm_units(stats.co2e), name: processName,
        type:'box', boxmean: true, boxpoints: false
      }
    );

    // Add energy to link to the right y-axis, hiding the object, hover info and legend itself
    plot_data_total.push(
      {
        x:processName, y: norm_units(stats.energy), name: processName,
        type:'box', boxmean: true, boxpoints: false, yaxis: 'y2', showlegend: false,
        hoverinfo: 'skip', marker: {color: 'rgba(0,0,0,0)'}, fillcolor: 'rgba(0,0,0,0)'
      }
    );

    // Add outline of CO2 emissions from non-cached processes to plot
    plot_data_non_cached.push(
      {
        x:processName, y: norm_units(stats.co2e_non_cached), name: processName,
        type:'box', boxmean: true, boxpoints: false,
      }
    );

    // Add energy to link to the right y-axis, hiding the object, hover info and legend itself
    plot_data_non_cached.push(
      {
        x:processName, y: norm_units(stats.energy_non_cached), name: processName,
        type:'box', boxmean: true, boxpoints: false, yaxis: 'y2', showlegend: false,
        hoverinfo: 'skip', marker: {color: 'rgba(0,0,0,0)'}, fillcolor: 'rgba(0,0,0,0)'
      }
    );
  }

  var layout = {
    title: 'CO<sub>2</sub> emission & energy consumption',
    legend: {
      x: 1.1
    },
    xaxis: {
      title: 'Processes',
    },
    yaxis: {
      title: 'CO2e emission (g)',
      rangemode: 'tozero',
    },
    yaxis2: {
      title: 'Energy consumption (Wh)',
      rangemode: 'tozero',
      gridcolor: 'rgba(0, 0, 0, 0)', // transparent grid lines
      overlaying: 'y',
      side: 'right',
    },
    boxmode: 'group',
  };

  Plotly.newPlot('co2e-total-plot', plot_data_total, layout);
  Plotly.newPlot('co2e-non-cached-plot', plot_data_non_cached, layout);

  //
  // Table creation functions
  //

  /**
   * Function to create the table of tasks
   */
  function make_tasks_table(){
    // reset
      if ( $.fn.dataTable.isDataTable( '#tasks_table' ) ) {
        $('#tasks_table').DataTable().destroy();
      }

      // Column titles
      var energyConsumptionTitle = 'Energy consumption (mWh)'; // Default column title
      var co2EmissionsTitle = 'CO₂e emissions (mg)';
      if ($('#nf-table-humanreadable').val() == 'true') {
        energyConsumptionTitle = 'Energy consumption'; // Change the column title if the button is selected
        co2EmissionsTitle = 'CO₂e emissions';
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
          { title: 'Carbon Intensity', data: 'ci', type: 'num', render: make_carbon_intensity },
          { title: 'realtime', data: 'time', type: 'num', render: make_time },
          { title: 'allocated cpus', data: 'cpus', type: 'num' },
          { title: 'power draw per core', data: 'powerdrawCPU', type: 'num', render: make_power_draw_cpu },
          { title: '%cpu', data: 'cpuUsage', type: 'num', render: make_core_usage_factor },
          { title: 'allocated memory', data: 'memory', type: 'num', render: make_memory },
          { title: 'cpu model', data: 'cpu_model' },
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

  // Executor for task table creation (on page load)
  if( window.data.trace==null ) {
      // Hide tasks table if too many tasks are present
      $('#tasks-present-table').remove()
  }
  else {
      $('#tasks-omitted-table').remove()
      // Dropdown changed about raw / human readable values in table
      $('#nf-table-humanreadable').change(function(){
        make_tasks_table();
      });
      // Make the table on page load
      make_tasks_table();
  }

  /**
   * Function to create the table of options / configurations
   */
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

  // Executor for options table creation (on page load)
  make_options_table();
});