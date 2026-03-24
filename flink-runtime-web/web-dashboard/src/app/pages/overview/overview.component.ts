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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { combineLatest, Observable, Subject } from 'rxjs';
import { map, mergeMap, share, takeUntil } from 'rxjs/operators';

import { ApplicationListComponent } from '@flink-runtime-web/components/application-list/application-list.component';
import { TopNMetricsComponent } from '@flink-runtime-web/components/topn-metrics/topn-metrics.component';
import {
  ApplicationItem,
  BackpressureOperatorInfo,
  CpuConsumerInfo,
  GcTaskInfo,
  OverviewWithApplicationStatistics
} from '@flink-runtime-web/interfaces';
import { OverviewStatisticComponent } from '@flink-runtime-web/pages/overview/statistic/overview-statistic.component';
import { ApplicationService, OverviewService, StatusService } from '@flink-runtime-web/services';

@Component({
  selector: 'flink-overview',
  templateUrl: './overview.component.html',
  styleUrls: ['./overview.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [OverviewStatisticComponent, ApplicationListComponent, TopNMetricsComponent]
})
export class OverviewComponent implements OnInit, OnDestroy {
  public applicationData$: Observable<ApplicationItem[]>;
  public statisticData$: Observable<OverviewWithApplicationStatistics>;

  // Top N Metrics data
  public topCpuConsumers: CpuConsumerInfo[] = [];
  public topBackpressureOperators: BackpressureOperatorInfo[] = [];
  public topGcIntensiveTasks: GcTaskInfo[] = [];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly statusService: StatusService,
    private readonly applicationService: ApplicationService,
    private readonly overviewService: OverviewService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  public ngOnInit(): void {
    const applications$ = this.statusService.refresh$.pipe(
      takeUntil(this.destroy$),
      mergeMap(() => this.applicationService.loadApplications()),
      share()
    );

    this.applicationData$ = applications$;
    this.statisticData$ = this.statusService.refresh$.pipe(
      takeUntil(this.destroy$),
      mergeMap(() => combineLatest([this.overviewService.loadOverview(), applications$])),
      map(([clusterOverview, applications]) => {
        const applicationStats: OverviewWithApplicationStatistics = {
          'applications-running': applications.filter(app => app.status === 'RUNNING').length,
          'applications-finished': applications.filter(app => app.status === 'FINISHED').length,
          'applications-cancelled': applications.filter(app => app.status === 'CANCELED').length,
          'applications-failed': applications.filter(app => app.status === 'FAILED').length,
          taskmanagers: clusterOverview['taskmanagers'],
          'taskmanagers-blocked': clusterOverview['taskmanagers-blocked'],
          'slots-total': clusterOverview['slots-total'],
          'slots-available': clusterOverview['slots-available'],
          'slots-free-and-blocked': clusterOverview['slots-free-and-blocked'],
          'flink-version': clusterOverview['flink-version'],
          'flink-commit': clusterOverview['flink-commit']
        };
        return applicationStats;
      }),
      share()
    );

    // Initialize Top N Metrics demo data
    this.initTopNMetricsData();
    this.cdr.markForCheck();
  }

  private initTopNMetricsData(): void {
    this.topCpuConsumers = [
      {
        subtaskId: 0,
        taskName: 'Source: Kafka',
        operatorName: 'Kafka Source',
        cpuPercentage: 95.5,
        taskManagerId: 'container_123'
      },
      {
        subtaskId: 1,
        taskName: 'Map: Process',
        operatorName: 'ProcessFunction',
        cpuPercentage: 88.2,
        taskManagerId: 'container_124'
      },
      {
        subtaskId: 2,
        taskName: 'Sink: HDFS',
        operatorName: 'HDFS Sink',
        cpuPercentage: 82.7,
        taskManagerId: 'container_125'
      },
      {
        subtaskId: 3,
        taskName: 'Window',
        operatorName: 'WindowFunction',
        cpuPercentage: 76.3,
        taskManagerId: 'container_123'
      },
      {
        subtaskId: 4,
        taskName: 'Aggregate',
        operatorName: 'AggregateFunction',
        cpuPercentage: 71.9,
        taskManagerId: 'container_126'
      }
    ];

    this.topBackpressureOperators = [
      { operatorId: 'op_456', operatorName: 'Map', backpressureRatio: 0.85, subtaskId: 1 },
      { operatorId: 'op_789', operatorName: 'Window', backpressureRatio: 0.72, subtaskId: 3 },
      { operatorId: 'op_234', operatorName: 'Filter', backpressureRatio: 0.58, subtaskId: 2 },
      { operatorId: 'op_567', operatorName: 'Join', backpressureRatio: 0.45, subtaskId: 4 },
      { operatorId: 'op_890', operatorName: 'Aggregate', backpressureRatio: 0.32, subtaskId: 5 }
    ];

    this.topGcIntensiveTasks = [
      { taskId: 'task_789', taskName: 'ProcessFunction', gcTimePercentage: 45.2, taskManagerId: 'container_123' },
      { taskId: 'task_456', taskName: 'WindowFunction', gcTimePercentage: 38.7, taskManagerId: 'container_124' },
      { taskId: 'task_234', taskName: 'JoinFunction', gcTimePercentage: 32.1, taskManagerId: 'container_125' },
      { taskId: 'task_567', taskName: 'AggregateFunction', gcTimePercentage: 28.5, taskManagerId: 'container_126' },
      { taskId: 'task_890', taskName: 'FilterFunction', gcTimePercentage: 24.3, taskManagerId: 'container_127' }
    ];
  }

  public ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  public navigateToApplication(commands: string[]): void {
    this.router.navigate(commands).then();
  }
}
