// JavaScript used to power the Nextflow Report Template output.

/**
 * Converts a list of numbers in milli-units (mWh, mg,...) to base units (Wh, g)
 * @param {ArrayLike} list An array with numbers
 * @returns The array, with each value divided by 1000
 */
function norm_units( list ) {
  return list?.map(v => v / 1000) ?? null;
}

/**
 * Decides whether raw or readable values are to be displayed
 *
 * @param {*} data The data (number/string/...)
 * @param {*} type Type of the data
 * @returns
 */
function rawOrReadable(data, type) {
  if (type === 'sort' || $('#nf-table-humanreadable').val() == 'false') {
    return data['raw'];
  }
  return data['readable']
}

// Map for collecting statistics by process
window.statsByProcess = {};

//
// MAIN BLOCK: This block is only executed after the page is fully loaded
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
      var energyConsumptionTitle = 'energy consumption (mWh)'; // Default column title
      var co2EmissionsTitle = 'CO₂e emissions (mg)';
      if ($('#nf-table-humanreadable').val() == 'true') {
        energyConsumptionTitle = 'energy consumption'; // Change the column title if the button is selected
        co2EmissionsTitle = 'CO₂e emissions';
      }

      var table = $('#tasks_table').DataTable({
        data: window.data.trace,
        columns: [
          { title: 'task_id', data: 'task_id' },
          { title: 'process', data: 'process' },
          { title: 'tag', data: 'tag' },
          { title: 'status', data: 'status' },
          { title: 'hash', data: 'hash' },
          { title: energyConsumptionTitle, data: 'energy' },
          { title: co2EmissionsTitle, data: 'co2e' },
          { title: `${co2EmissionsTitle} (market)`, data: 'co2eMarket' },
          { title: 'carbon intensity', data: 'ci' },
          { title: 'allocated cpus', data: 'cpus' },
          { title: '%cpu', data: 'cpuUsage' },
          { title: 'allocated memory', data: 'memory' },
          { title: 'realtime', data: 'time' },
          { title: 'power draw (in W/core)', data: 'powerdrawCPU' },
          { title: 'cpu model', data: 'cpu_model' },
        ],
        "deferRender": true,
        "lengthMenu": [[25, 50, 100, -1], [25, 50, 100, "All"]],
        "scrollX": true,
        "colReorder": true,
        "columnDefs": [
          { targets: '_all', render: rawOrReadable },
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