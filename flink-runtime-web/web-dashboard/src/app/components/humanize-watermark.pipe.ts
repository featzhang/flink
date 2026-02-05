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

import { Pipe, PipeTransform } from '@angular/core';

import { ConfigService } from '@flink-runtime-web/services';

export type WatermarkDisplayFormat = 'iso8601' | 'locale' | 'custom' | 'raw';
export type WatermarkTimezone = 'utc' | 'local';

export interface WatermarkFormatOptions {
  format?: WatermarkDisplayFormat;
  timezone?: WatermarkTimezone;
  customFormat?: string;
  showMilliseconds?: boolean;
}

@Pipe({
  name: 'humanizeWatermark'
})
export class HumanizeWatermarkPipe implements PipeTransform {
  constructor(private readonly configService: ConfigService) {}

  public transform(value: number): number | string {
    if (isNaN(value) || value <= this.configService.LONG_MIN_VALUE) {
      return 'No Watermark (Watermarks are only available if EventTime is used)';
    } else {
      // Show raw timestamp by default for the watermark column
      return value;
    }
  }
}

@Pipe({
  name: 'humanizeWatermarkToDatetime'
})
export class HumanizeWatermarkToDatetimePipe implements PipeTransform {
  constructor(private readonly configService: ConfigService) {}

  public transform(value: number, options?: WatermarkFormatOptions): number | string {
    if (value == null || isNaN(value) || value <= this.configService.LONG_MIN_VALUE) {
      return 'N/A';
    }

    const format = options?.format || 'locale';
    const timezone = options?.timezone || 'local';
    const showMilliseconds = options?.showMilliseconds !== false;

    try {
      const date = new Date(value);

      switch (format) {
        case 'raw':
          return value;

        case 'iso8601':
          return this.formatISO8601(date, timezone, showMilliseconds);

        case 'custom':
          if (options?.customFormat) {
            return this.formatCustom(date, options.customFormat, timezone);
          }
          // Fallback to locale if custom format not provided
          return this.formatLocale(date, timezone, showMilliseconds);

        case 'locale':
        default:
          return this.formatLocale(date, timezone, showMilliseconds);
      }
    } catch (error) {
      console.error('Error formatting watermark timestamp:', error);
      return `Invalid Date (${value})`;
    }
  }
  private formatISO8601(date: Date, timezone: WatermarkTimezone, showMilliseconds: boolean): string {
    if (timezone === 'utc') {
      const isoString = date.toISOString();
      return showMilliseconds ? isoString : isoString.substring(0, 19) + 'Z';
    }
    // Local timezone in ISO8601 format
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    const ms = String(date.getMilliseconds()).padStart(3, '0');

    const timezoneOffset = -date.getTimezoneOffset();
    const offsetHours = String(Math.floor(Math.abs(timezoneOffset) / 60)).padStart(2, '0');
    const offsetMinutes = String(Math.abs(timezoneOffset) % 60).padStart(2, '0');
    const offsetSign = timezoneOffset >= 0 ? '+' : '-';

    const msString = showMilliseconds ? `.${ms}` : '';
    return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}${msString}${offsetSign}${offsetHours}:${offsetMinutes}`;
  }
      result += `${offsetSign}${offsetHours}:${offsetMinutes}`;

      return result;
    }
  }

  private formatLocale(date: Date, timezone: WatermarkTimezone, showMilliseconds: boolean): string {
    let formatted: string;

    if (timezone === 'utc') {
      // Format as UTC
      formatted = date.toLocaleString('en-US', {
        timeZone: 'UTC',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false
      });
      formatted += ' UTC';
    } else {
      // Format with local timezone
      formatted = date.toLocaleString(undefined, {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false
      });
    }

    if (showMilliseconds) {
      const ms = String(date.getMilliseconds()).padStart(3, '0');
      // Insert milliseconds before timezone info
      const parts = formatted.split(' ');
      if (parts.length > 1 && timezone === 'utc') {
        // For UTC: "MM/DD/YYYY, HH:mm:ss UTC" -> "MM/DD/YYYY, HH:mm:ss.SSS UTC"
        parts[parts.length - 2] += `.${ms}`;
        formatted = parts.join(' ');
      } else {
        // For local: "MM/DD/YYYY, HH:mm:ss" -> "MM/DD/YYYY, HH:mm:ss.SSS"
        formatted += `.${ms}`;
      }
    }

    return formatted;
  }

  private formatCustom(date: Date, format: string, timezone: WatermarkTimezone): string {
    // Simple custom format implementation
    // Supported tokens: YYYY, MM, DD, HH, mm, ss, SSS
    const d = timezone === 'utc' ? this.getUTCComponents(date) : this.getLocalComponents(date);

    return format
      .replace('YYYY', d.year)
      .replace('MM', d.month)
      .replace('DD', d.day)
      .replace('HH', d.hours)
      .replace('mm', d.minutes)
      .replace('ss', d.seconds)
      .replace('SSS', d.milliseconds);
  }

  private getUTCComponents(date: Date): Record<string, string> {
    return {
      year: String(date.getUTCFullYear()),
      month: String(date.getUTCMonth() + 1).padStart(2, '0'),
      day: String(date.getUTCDate()).padStart(2, '0'),
      hours: String(date.getUTCHours()).padStart(2, '0'),
      minutes: String(date.getUTCMinutes()).padStart(2, '0'),
      seconds: String(date.getUTCSeconds()).padStart(2, '0'),
      milliseconds: String(date.getUTCMilliseconds()).padStart(3, '0')
    };
  }

  private getLocalComponents(date: Date): Record<string, string> {
    return {
      year: String(date.getFullYear()),
      month: String(date.getMonth() + 1).padStart(2, '0'),
      day: String(date.getDate()).padStart(2, '0'),
      hours: String(date.getHours()).padStart(2, '0'),
      minutes: String(date.getMinutes()).padStart(2, '0'),
      seconds: String(date.getSeconds()).padStart(2, '0'),
      milliseconds: String(date.getMilliseconds()).padStart(3, '0')
    };
  }
}
