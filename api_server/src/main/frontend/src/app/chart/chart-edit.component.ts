import { Component }                                   from '@angular/core';
import { ChartExprFormComponent }                      from './chart-expr-form.component';
import { ChartExpr }                                   from './chart-expr';
import { Router, ActivatedRoute }                      from '@angular/router';
import { Observable }                                  from 'rxjs/Observable';
import                                                      'rxjs/add/operator/map';
import { encodeExpr, decodeExpr }                      from './chart-edit-arguments.service';


@Component({
  selector: 'chart-edit',
  styles: [`
    .chart-edit {
      width: 100%;
      height: 100%;
    }
  `],
  template: `
    <h1>Monsoon Chart</h1>
    <chart-expr-form #form (expr)="onRender($event)" [initial]="exprs | async"></chart-expr-form>
    `
})
export class ChartEditComponent {
  private exprs: Observable<Map<string, string>>;

  constructor(private router: Router, private route: ActivatedRoute) {
    this.exprs = this.route
        .params
        .map((params) => ChartEditComponent._paramsToExprMap(params));
  }

  onRender(m: ChartExpr) {
    let args = {};
    Object.keys(m.expr).forEach((k) => {
      args[encodeExpr('expr:' + k)] = encodeExpr(m.expr[k]);
    });

    this.router.navigate(['/fe/chart-view', args], {preserveQueryParams: true});
  }

  private static _paramsToExprMap(params: { [key: string]: any; }): Map<string, string> {
    let exprs = new Map<string, string>();

    Object.keys(params).forEach((enc_k) => {
      let k: string = decodeExpr(enc_k);

      if (k.startsWith("expr:")) {
        let v: string = decodeExpr(params[enc_k]);
        exprs[k.substr(5)] = v;
      }
    });
    return exprs;
  }
}
