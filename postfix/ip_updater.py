#!/usr/bin/env python3
"""
Atlassian IP Whitelist Updater

Automatically updates Postfix client whitelist with current Atlassian email server IPs.
Sends alerts and metrics to DataDog for monitoring and alerting.
"""

import os
import json
import time
import logging
import requests
import subprocess
import hashlib
from datetime import datetime
from typing import List, Dict, Optional

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class AtlassianIPUpdater:
    def __init__(self):
        # Configuration
        self.atlassian_url = os.getenv('ATLASSIAN_IP_JSON_URL', 'https://ip-ranges.atlassian.com/')
        self.datadog_api_key = os.getenv('DATADOG_API_KEY')
        self.datadog_app_key = os.getenv('DATADOG_APP_KEY')
        self.datadog_site = os.getenv('DATADOG_SITE', 'datadoghq.com')  # Default to US site
        self.datadog_hostname = os.getenv('DATADOG_HOSTNAME', "unknown")

        self.check_interval = int(os.getenv('IP_CHECK_INTERVAL', '3600'))
        self.metric_interval = int(os.getenv('METRIC_REPORT_INTERVAL', '300'))  # 5 minutes default
        
        # File paths
        self.cidr_file_path = '/etc/postfix/clients.cidr'
        self.state_file = '/tmp/atlassian_state.json'
        
        # Tracking for metric reporting
        self.last_metric_time = 0
    
    def load_state(self) -> Dict:
        """Load previous state from file"""
        try:
            if os.path.exists(self.state_file):
                with open(self.state_file, 'r') as f:
                    return json.load(f)
        except Exception as e:
            logger.debug(f"Could not load state: {e}")
        return {}
    
    def save_state(self, state: Dict) -> None:
        """Save current state to file"""
        try:
            with open(self.state_file, 'w') as f:
                json.dump(state, f)
        except Exception as e:
            logger.error(f"Could not save state: {e}")
    
    def send_datadog_event(self, title: str, text: str, alert_type: str = 'info', tags: Optional[List[str]] = None) -> None:
        """Send event to DataDog if configured"""
        if not self.datadog_api_key:
            logger.debug("DataDog API key not configured, skipping notification")
            return
            
        if tags is None:
            tags = ['service:postfix', 'component:ip-updater', f"host:{self.datadog_hostname}"]
        
        # DataDog Events API endpoint
        url = f"https://api.{self.datadog_site}/api/v1/events"
        
        headers = {
            'Content-Type': 'application/json',
            'DD-API-KEY': self.datadog_api_key
        }
        
        # Add app key if available (required for some operations)
        if self.datadog_app_key:
            headers['DD-APPLICATION-KEY'] = self.datadog_app_key
        
        payload = {
            "title": title,
            "text": text,
            "date_happened": int(datetime.now().timestamp()),
            "priority": "normal",
            "tags": tags,
            "alert_type": alert_type,  # info, error, warning, success
            "source_type_name": "postfix-ip-updater"
        }
        
        try:
            response = requests.post(url, json=payload, headers=headers, timeout=10)
            response.raise_for_status()
            logger.info(f"DataDog event sent: {title}")
        except Exception as e:
            logger.error(f"Failed to send DataDog event: {e}")

    def send_datadog_metric(self, metric_name: str, value: float, tags: Optional[List[str]] = None) -> None:
        """Send metric to DataDog if configured"""
        if not self.datadog_api_key:
            return
            
        if tags is None:
            tags = ['service:postfix', 'component:ip-updater', f"host:{self.datadog_hostname}"]
        
        # DataDog Metrics API endpoint
        url = f"https://api.{self.datadog_site}/api/v1/series"
        
        headers = {
            'Content-Type': 'application/json',
            'DD-API-KEY': self.datadog_api_key
        }
        
        # Add app key if available
        if self.datadog_app_key:
            headers['DD-APPLICATION-KEY'] = self.datadog_app_key
        
        payload = {
            "series": [{
                "metric": metric_name,
                "points": [[int(datetime.now().timestamp()), value]],
                "type": "gauge",
                "tags": tags
            }]
        }
        
        try:
            response = requests.post(url, json=payload, headers=headers, timeout=10)
            response.raise_for_status()
            logger.debug(f"DataDog metric sent: {metric_name}={value}, tags: {tags}")
        except Exception as e:
            logger.error(f"Failed to send DataDog metric: {e}")

    def fetch_atlassian_data(self, last_hash: Optional[str] = None) -> Optional[Dict]:
        """Fetch Atlassian IP data and check if it has changed"""
        try:
            response = requests.get(self.atlassian_url, timeout=30)
            response.raise_for_status()
            
            # Check if content changed
            content = response.text
            current_hash = hashlib.md5(content.encode()).hexdigest()
            
            if last_hash and current_hash == last_hash:
                logger.info("No changes detected")
                return None
                
            data = response.json()
            if 'items' not in data:
                raise ValueError("Invalid JSON structure")
                
            # Add hash to data for later saving
            data['_content_hash'] = current_hash
            return data
            
        except Exception as e:
            logger.error(f"Failed to fetch Atlassian data: {e}")
            return None
    
    def extract_email_ips(self, data: Dict) -> List[str]:
        """Extract email egress IP ranges"""
        email_ips = []
        for item in data.get('items', []):
            if ('email' in item.get('product', []) and 
                'egress' in item.get('direction', []) and 
                item.get('cidr')):
                email_ips.append(item['cidr'])
        
        return sorted(email_ips)
    
    def update_cidr_file(self, ip_ranges: List[str]) -> bool:
        """Update the CIDR file with new IP ranges"""
        try:
            # Create backup if file exists
            if os.path.exists(self.cidr_file_path):
                backup_path = f"{self.cidr_file_path}.backup.{int(time.time())}"
                subprocess.run(['cp', self.cidr_file_path, backup_path], check=True)
            
            # Generate content
            content_lines = [
                f"# Generated at {datetime.now().strftime('%Y-%m-%d %H:%M:%S UTC')}",
                f"# Source: {self.atlassian_url}",
                f"# Total IP ranges: {len(ip_ranges)}",
                ""
            ]
            content_lines.extend(f"{ip_range} OK" for ip_range in ip_ranges)
            content_lines.append("")
            
            # Write file
            os.makedirs(os.path.dirname(self.cidr_file_path), exist_ok=True)
            with open(self.cidr_file_path, 'w') as f:
                f.write('\n'.join(content_lines))
            
            logger.info(f"Updated {self.cidr_file_path} with {len(ip_ranges)} ranges")
            return True
            
        except Exception as e:
            logger.error(f"Failed to update CIDR file: {e}")
            return False

    def reload_postfix(self) -> bool:
        """Reload Postfix configuration"""
        try:
            # Check if Postfix is running
            check_result = subprocess.run(['postfix', 'status'], 
                                        capture_output=True, text=True, timeout=10)
            
            if check_result.returncode != 0:
                logger.info("Postfix is not running, starting it...")
                start_result = subprocess.run(['postfix', 'start'], 
                                            capture_output=True, text=True, timeout=10)
                
                if start_result.returncode == 0:
                    logger.info("Postfix started successfully")
                else:
                    logger.error(f"Failed to start Postfix: {start_result.stderr}")
                    return False
            
            # Reload Postfix to pick up the new configuration
            result = subprocess.run(['postfix', 'reload'], 
                                  capture_output=True, text=True, timeout=10)
            
            if result.returncode == 0:
                logger.info("Postfix configuration reloaded successfully")
                return True
            else:
                logger.error(f"Failed to reload Postfix: {result.stderr}")
                return False
            
        except Exception as e:
            logger.error(f"Failed to reload Postfix: {e}")
            return False
    
    def send_health_metrics(self) -> None:
        """Send health metrics based on current state"""
        if not self.datadog_api_key:
            return
            
        # Load current state
        state = self.load_state()
        
        # Send current IP count from state (or 0 if not available)
        ip_count = state.get('ip_count', 0)
        self.send_datadog_metric("atlassian.ip_updater.ip_count", ip_count)
        
        # Send health check metric to show the service is running
        self.send_datadog_metric("atlassian.ip_updater.postfix_reload_success", 1)
        
        # Update last metric time
        self.last_metric_time = time.time()
        
        logger.debug(f"Health metrics sent to DataDog (IP count: {ip_count})")

    def should_send_metrics(self) -> bool:
        """Check if it's time to send metrics"""
        current_time = time.time()
        return (current_time - self.last_metric_time) >= self.metric_interval

    def update_ips(self) -> bool:
        """Main update process"""
        logger.info("Checking for Atlassian IP updates...")
        
        # Load previous state
        state = self.load_state()
        last_hash = state.get('content_hash')
        
        # Fetch data
        data = self.fetch_atlassian_data(last_hash)
        if data is None:
            # No update needed, but send current metrics for monitoring
            ip_count = state.get('ip_count', 0)
            self.send_datadog_metric("atlassian.ip_updater.ip_count", ip_count)
            self.send_datadog_metric("atlassian.ip_updater.postfix_reload_success", 1)  # Assume healthy if no update needed
            return True
        
        # Extract IPs
        email_ips = self.extract_email_ips(data)
        if not email_ips:
            logger.error("No email IPs found")
            self.send_datadog_event(
                "Atlassian IP Update Error", 
                "No email IPs found in Atlassian data", 
                'error'
            )
            return False
        
        # Update file
        if not self.update_cidr_file(email_ips):
            self.send_datadog_event(
                "Atlassian IP Update Error", 
                "Failed to update CIDR file", 
                'error'
            )
            return False
        
        # Reload Postfix
        postfix_success = self.reload_postfix()
        
        # Save state
        state['content_hash'] = data['_content_hash']
        state['last_update'] = datetime.now().isoformat()
        state['ip_count'] = len(email_ips)
        self.save_state(state)
        
        # Send notification
        status = "✅" if postfix_success else "⚠️"
        postfix_msg = "and reloaded Postfix" if postfix_success else "but failed to reload Postfix"
        alert_type = 'success' if postfix_success else 'warning'
        
        self.send_datadog_event(
            "Atlassian IP Whitelist Updated",
            f"{status} Updated email whitelist with {len(email_ips)} IP ranges {postfix_msg}",
            alert_type
        )
        
        # Send metrics to DataDog
        self.send_datadog_metric("atlassian.ip_updater.ip_count", len(email_ips))
        self.send_datadog_metric("atlassian.ip_updater.postfix_reload_success", 1 if postfix_success else 0)
        
        return postfix_success
    
    def run(self):
        """Main loop"""
        logger.info("Starting Atlassian IP Updater")
        logger.info(f"Check interval: {self.check_interval} seconds")
        logger.info(f"Metric report interval: {self.metric_interval} seconds")
        logger.info(f"Atlassian URL: {self.atlassian_url}")
        logger.info(f"CIDR file: {self.cidr_file_path}")
        
        # Send startup notification
        if self.datadog_api_key:
            self.send_datadog_event(
                "IP Updater Started", 
                "IP updater started inside Postfix container", 
                'info'
            )
        
        # Initial update and first metric send
        self.update_ips()
        self.send_health_metrics()
        
        # Track last IP check time
        last_ip_check = time.time()
        
        # Main loop with 30-second intervals to check both timers
        while True:
            try:
                current_time = time.time()
                
                # Check if it's time for IP update
                if (current_time - last_ip_check) >= self.check_interval:
                    self.update_ips()
                    last_ip_check = current_time
                
                # Check if it's time for metric reporting (independent of IP checks)
                elif self.should_send_metrics():
                    self.send_health_metrics()
                
                # Sleep for 30 seconds to avoid busy waiting
                time.sleep(30)
                
            except Exception as e:
                logger.error(f"Error during update: {e}")

if __name__ == "__main__":
    updater = AtlassianIPUpdater()
    try:
        updater.run()
    except KeyboardInterrupt:
        logger.info("IP updater stopped by user")
    except Exception as e:
        logger.error(f"IP updater crashed: {e}")
        if updater.datadog_api_key:
            updater.send_datadog_event(
                "IP Updater Crashed", 
                f"IP updater crashed: {e}", 
                'error'
            )
