(function(global) {
  System.config({
    paths: {
      // paths serve as an alias
      'npm:': 'node_modules/'
    },

    // map tells the System loader where to look for things
    map: {
      // app is in the app folder
      app:                                 'app',

      // angular bundles
      '@angular/core':                     'npm:@angular/core/bundles/core.umd.js',
      '@angular/common':                   'npm:@angular/common/bundles/common.umd.js',
      '@angular/compiler':                 'npm:@angular/compiler/bundles/compiler.umd.js',
      '@angular/platform-browser':         'npm:@angular/platform-browser/bundles/platform-browser.umd.js',
      '@angular/platform-browser-dynamic': 'npm:@angular/platform-browser-dynamic/bundles/platform-browser-dynamic.umd.js',
      '@angular/http':                     'npm:@angular/http/bundles/http.umd.js',
      '@angular/router':                   'npm:@angular/router/bundles/router.umd.js',
      '@angular/forms':                    'npm:@angular/forms/bundles/forms.umd.js',

      // other libraries
      'rxjs':                              'npm:rxjs',
      'angular2-in-memory-web-api':        'npm:angular2-in-memory-web-api',

      'ng2-bs3-modal':                     'npm:ng2-bs3-modal'
    },

    // packages tells the System loader how to load when no filename and/or no extension
    packages: {
      app:                                 { main: 'main.js',  defaultExtension: 'js' },
      rxjs:                                { defaultExtension: 'js' },
      'angular2-in-memory-web-api':        { defaultExtension: 'js' },
      'ng2-bs3-modal':                     { defaultExtension: 'js' },
    }
  });
})(this);
