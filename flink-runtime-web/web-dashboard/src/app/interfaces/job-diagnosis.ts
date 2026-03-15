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

import { Params } from '@angular/router';
import { mergeMap } from 'rxjs';
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { JobsService } from './jobs.service';
import { JobDetailParams } from './interfaces';

/**
 * Represents a diagnostic message with severity, title, description, and suggested actions.
 */
export interface DiagnosticMessage {
  severity: 'critical' | 'warning' | 'info';
  title: string;
  message: string;
  metrics: { [key: string]: any };
  suggestedActions: string[];
}

/**
 * Response body containing diagnostic messages and suggestions.
 */
export interface DiagnosisResponseBody {
  diagnostics: DiagnosticMessage[];
  timestamp: number;
}

/**
 * Service for handling job diagnosis-related operations.
 */
@Injectable()
export class DiagnosisService {
  constructor(private httpClient: HttpClient, private jobsService: JobsService) {}

  /**
   * Retrieves diagnosis information for a specific job.
   * @param jobId The ID of the job
   * @returns Observable containing diagnosis information
   */
  getDiagnosis(jobId: string) {
    return this.httpClient.get<DiagnosisResponseBody>(
      this.jobsService.getJobDetailUrl(jobId) + '/diagnosis'
    );
  }
}

/**
 * Provider for DiagnosisService.
 */
export const DIAGNOSIS_SERVICE_PROVIDER = {
  provide: DiagnosisService,
  useFactory: (httpClient: HttpClient, jobsService: JobsService) => {
    return new DiagnosisService(httpClient, jobsService);
  },
  deps: [HttpClient, JobsService]
};
