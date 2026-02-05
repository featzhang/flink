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

import { NgIf } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { of, Subject } from 'rxjs';
import { catchError, map, mergeMap, takeUntil } from 'rxjs/operators';

import {
  HumanizeWatermarkPipe,
  HumanizeWatermarkToDatetimePipe,
  WatermarkDisplayFormat,
  WatermarkFormatOptions,
  WatermarkTimezone
} from '@flink-runtime-web/components/humanize-watermark.pipe';
import { MetricsService } from '@flink-runtime-web/services';
import { typeDefinition } from '@flink-runtime-web/utils';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';

import { JobLocalService } from '../../job-local.service';

interface WatermarkData {
  subTaskIndex: number;
  watermark: number;
}

const WATERMARK_FORMAT_STORAGE_KEY = 'flink.watermark.format.preferences';

interface WatermarkFormatPreferences {
  format: WatermarkDisplayFormat;
  timezone: WatermarkTimezone;
  showMilliseconds: boolean;
}

@Component({
  selector: 'flink-job-overview-drawer-watermarks',
  templateUrl: './job-overview-drawer-watermarks.component.html',
  styleUrls: ['./job-overview-drawer-watermarks.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NzTableModule,
    NgIf,
    HumanizeWatermarkPipe,
    HumanizeWatermarkToDatetimePipe,
    NzIconModule,
    NzToolTipModule,
    NzSelectModule,
    NzRadioModule,
    FormsModule
  ]
})
export class JobOverviewDrawerWatermarksComponent implements OnInit, OnDestroy {
  public readonly trackBySubtaskIndex = (_: number, node: { subTaskIndex: number; watermark: number }): number =>
    node.subTaskIndex;

  public listOfWaterMark: WatermarkData[] = [];
  public isLoading = true;
  public virtualItemSize = 36;
  public readonly narrowLogData = typeDefinition<WatermarkData>();

  // Format options
  public displayFormat: WatermarkDisplayFormat = 'locale';
  public timezone: WatermarkTimezone = 'local';
  public showMilliseconds = true;

  public readonly formatOptions = [
    { label: 'Locale Format', value: 'locale' as WatermarkDisplayFormat },
    { label: 'ISO 8601', value: 'iso8601' as WatermarkDisplayFormat },
    { label: 'Raw Timestamp', value: 'raw' as WatermarkDisplayFormat }
  ];

  public readonly timezoneOptions = [
    { label: 'Local Time', value: 'local' as WatermarkTimezone },
    { label: 'UTC', value: 'utc' as WatermarkTimezone }
  ];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly jobLocalService: JobLocalService,
    private readonly metricsService: MetricsService,
    private readonly cdr: ChangeDetectorRef
  ) {
    this.loadFormatPreferences();
  }

  public ngOnInit(): void {
    this.jobLocalService
      .jobWithVertexChanges()
      .pipe(
        mergeMap(data =>
          this.metricsService.loadWatermarks(data.job.jid, data.vertex!.id).pipe(
            map(data => {
              const list = [];
              for (const key in data.watermarks) {
                list.push({
                  subTaskIndex: +key,
                  watermark: data.watermarks[key]
                } as WatermarkData);
              }
              return list;
            }),
            catchError(() => {
              return of([] as WatermarkData[]);
            })
          )
        ),
        takeUntil(this.destroy$)
      )
      .subscribe(list => {
        this.isLoading = false;
        this.listOfWaterMark = list;
        this.cdr.markForCheck();
      });
  }

  public ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  public sortWatermark(a: WatermarkData, b: WatermarkData): number {
    return a.watermark - b.watermark;
  }

  public get formatOptions_watermark(): WatermarkFormatOptions {
    return {
      format: this.displayFormat,
      timezone: this.timezone,
      showMilliseconds: this.showMilliseconds
    };
  }

  public onFormatChange(): void {
    this.saveFormatPreferences();
    this.cdr.markForCheck();
  }

  public onTimezoneChange(): void {
    this.saveFormatPreferences();
    this.cdr.markForCheck();
  }

  public onMillisecondsToggle(): void {
    this.saveFormatPreferences();
    this.cdr.markForCheck();
  }

  private loadFormatPreferences(): void {
    try {
      const stored = localStorage.getItem(WATERMARK_FORMAT_STORAGE_KEY);
      if (stored) {
        const preferences: WatermarkFormatPreferences = JSON.parse(stored);
        this.displayFormat = preferences.format || 'locale';
        this.timezone = preferences.timezone || 'local';
        this.showMilliseconds = preferences.showMilliseconds !== false;
      }
    } catch (error) {
      console.warn('Failed to load watermark format preferences:', error);
    }
  }

  private saveFormatPreferences(): void {
    try {
      const preferences: WatermarkFormatPreferences = {
        format: this.displayFormat,
        timezone: this.timezone,
        showMilliseconds: this.showMilliseconds
      };
      localStorage.setItem(WATERMARK_FORMAT_STORAGE_KEY, JSON.stringify(preferences));
    } catch (error) {
      console.warn('Failed to save watermark format preferences:', error);
    }
  }
}
