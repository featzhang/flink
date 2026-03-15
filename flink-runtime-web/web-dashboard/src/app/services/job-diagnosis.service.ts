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

import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ConfigService } from './config.service';

export interface DiagnosticMessage {
  severity: 'critical' | 'warning' | 'info';
  title: string;
  message: string;
  metrics: { [key: string]: any };
  suggestedActions: string[];
}

export interface DiagnosisResponseBody {
  diagnostics: DiagnosticMessage[];
  timestamp: number;
}

@Injectable({
  providedIn: 'root'
})
export class JobDiagnosisService {
  constructor(private readonly httpClient: HttpClient, private readonly configService: ConfigService) {}

  getJobDetailUrl(jobId: string): string {
    return `${this.configService.getBaseURL()}jobs/${jobId}`;
  }

  getDiagnosis(jobId: string): Observable<DiagnosisResponseBody> {
    return this.httpClient.get<DiagnosisResponseBody>(
      this.getJobDetailUrl(jobId) + '/diagnosis'
    );
  }
}
