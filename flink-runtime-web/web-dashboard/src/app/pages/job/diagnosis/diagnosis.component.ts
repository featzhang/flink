/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { JobDiagnosisService, DiagnosticMessage } from '@flink-runtime-web/services';

@Component({
  selector: 'flink-job-diagnosis',
  templateUrl: './diagnosis.component.html',
  styleUrls: ['./diagnosis.component.less']
})
export class DiagnosisComponent implements OnInit, OnDestroy {
  isLoading = true;
  diagnosticMessages: DiagnosticMessage[] = [];
  errorMessage: string | null = null;
  jobId = '';

  private unsubscribe$ = new Subject<void>();

  constructor(
    private route: ActivatedRoute,
    private jobDiagnosisService: JobDiagnosisService
  ) {}

  ngOnInit(): void {
    this.jobId = this.route.snapshot.paramMap.get('jid') || '';
    this.loadDiagnosis();
  }

  ngOnDestroy(): void {
    this.unsubscribe$.next();
    this.unsubscribe$.complete();
  }

  loadDiagnosis(): void {
    this.isLoading = true;
    this.errorMessage = null;
    
    this.jobDiagnosisService
      .getDiagnosis(this.jobId)
      .pipe(takeUntil(this.unsubscribe$))
      .subscribe({
        next: (response) => {
          this.diagnosticMessages = response.diagnostics;
          this.isLoading = false;
        },
        error: (error) => {
          console.error('Failed to load diagnosis:', error);
          this.errorMessage = 'Failed to load diagnosis information. Please try again.';
          this.isLoading = false;
        }
      });
  }

  getSeverityClass(severity: string): string {
    switch (severity) {
      case 'critical':
        return 'severity-critical';
      case 'warning':
        return 'severity-warning';
      case 'info':
        return 'severity-info';
      default:
        return '';
    }
  }

  getSeverityIcon(severity: string): string {
    switch (severity) {
      case 'critical':
        return 'error';
      case 'warning':
        return 'warning';
      case 'info':
        return 'info';
      default:
        return 'info';
    }
  }

  metricEntries(metrics: { [key: string]: any }): Array<{ key: string; value: string }> {
    return Object.entries(metrics).map(([key, value]) => ({
      key,
      value: this.formatMetricValue(value)
    }));
  }

  private formatMetricValue(value: any): string {
    if (typeof value === 'number') {
      if (value >= 1000000) {
        return (value / 1000000).toFixed(2) + 'M';
      } else if (value >= 1000) {
        return (value / 1000).toFixed(2) + 'K';
      } else {
        return value.toString();
      }
    }
    return String(value);
  }

  refresh(): void {
    this.loadDiagnosis();
  }
}
