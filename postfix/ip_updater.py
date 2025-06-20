#!/usr/bin/env python3
"""
Atlassian IP Whitelist Updater

Automatically updates Postfix client whitelist with current Atlassian email server IPs.
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
        self.slack_webhook_url = os.getenv('SLACK_WEBHOOK_URL')
        self.check_interval = int(os.getenv('IP_CHECK_INTERVAL', '3600'))
        
        # File paths
        self.cidr_file_path = '/etc/postfix/clients.cidr'
        self.state_file = '/tmp/atlassian_state.json'
    
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
    
    def send_slack_notification(self, message: str, severity: str = 'info') -> None:
        """Send notification to Slack if configured"""
        if not self.slack_webhook_url:
            return
            
        colors = {'success': '#00ff00', 'warning': '#ffaa00', 'error': '#ff0000', 'info': '#0099cc'}
        
        payload = {
            "attachments": [{
                "color": colors.get(severity, '#0099cc'),
                "title": "Atlassian IP Whitelist Update",
                "text": message,
                "ts": int(datetime.now().timestamp())
            }]
        }
        
        try:
            requests.post(self.slack_webhook_url, json=payload, timeout=10)
            logger.info(f"Slack notification sent: {message}")
        except Exception as e:
            logger.error(f"Failed to send Slack notification: {e}")

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
    
    def update_ips(self) -> bool:
        """Main update process"""
        logger.info("Checking for Atlassian IP updates...")
        
        # Load previous state
        state = self.load_state()
        last_hash = state.get('content_hash')
        
        # Fetch data
        data = self.fetch_atlassian_data(last_hash)
        if data is None:
            return True  # No update needed
        
        # Extract IPs
        email_ips = self.extract_email_ips(data)
        if not email_ips:
            logger.error("No email IPs found")
            self.send_slack_notification("No email IPs found in Atlassian data", 'error')
            return False
        
        # Update file
        if not self.update_cidr_file(email_ips):
            self.send_slack_notification("Failed to update CIDR file", 'error')
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
        self.send_slack_notification(
            f"{status} Updated email whitelist with {len(email_ips)} IP ranges {postfix_msg}",
            'success' if postfix_success else 'warning'
        )
        
        return postfix_success
    
    def run(self):
        """Main loop"""
        logger.info("Starting Atlassian IP Updater")
        logger.info(f"Check interval: {self.check_interval} seconds")
        logger.info(f"Atlassian URL: {self.atlassian_url}")
        logger.info(f"CIDR file: {self.cidr_file_path}")
        
        # Send startup notification
        if self.slack_webhook_url:
            self.send_slack_notification("IP updater started inside Postfix container", 'info')
        
        # Initial update
        self.update_ips()
        
        # Main loop
        while True:
            try:
                time.sleep(self.check_interval)
                self.update_ips()
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
        if updater.slack_webhook_url:
            updater.send_slack_notification(f"IP updater crashed: {e}", 'error')
