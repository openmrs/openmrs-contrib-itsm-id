#!/usr/bin/env python3
"""
Health Check Service for Postfix

Provides HTTP endpoints for monitoring Postfix service health.
"""

import os
import json
import time
import logging
import subprocess
import threading
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse
from typing import Dict, Optional

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('health_check')

class HealthCheckHandler(BaseHTTPRequestHandler):
    """HTTP handler for health check endpoints"""
    
    def do_GET(self):
        """Handle GET requests"""
        parsed_path = urlparse(self.path)
        
        if parsed_path.path == '/postfix':
            self.handle_health_check()
        elif parsed_path.path == '/status':
            self.handle_status_check()
        elif parsed_path.path == '/':
            self.handle_root()
        else:
            self.send_error(404, "Not Found")
    
    def handle_root(self):
        """Root endpoint with service info"""
        response = {
            "service": "postfix-health-check",
            "endpoints": ["/postfix", "/status"],
            "timestamp": datetime.now().isoformat()
        }
        
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(response, indent=2).encode())
    
    def handle_health_check(self):
        """Basic health check endpoint for monitoring services like Pingdom"""
        try:
            health_data = self.get_health_status()
            
            status_code = 200 if health_data['healthy'] else 503
            
            response = {
                "status": "OK" if health_data['healthy'] else "Service Unavailable",
                "healthy": health_data['healthy'],
                "timestamp": datetime.now().isoformat(),
                "checks": {
                    "postfix_running": health_data['postfix_running'],
                    "postfix_queue_healthy": health_data['queue_healthy'],
                    "config_valid": health_data['config_valid']
                }
            }
            
            self.send_response(status_code)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(response, indent=2).encode())
            
        except Exception as e:
            logger.error(f"Health check error: {e}")
            self.send_error(500, "Internal Server Error")
    
    def handle_status_check(self):
        """Detailed status endpoint for debugging"""
        try:
            health_data = self.get_health_status()
            postfix_metrics = self.get_postfix_metrics()
            
            response = {
                "service": "postfix-mail-server",
                "version": "1.0.0",
                "timestamp": datetime.now().isoformat(),
                "health": health_data,
                "postfix": postfix_metrics,
                "files": {
                    "main_config": os.path.exists('/etc/postfix/main.cf'),
                    "master_config": os.path.exists('/etc/postfix/master.cf'),
                    "clients_cidr": os.path.exists('/etc/postfix/clients.cidr'),
                    "sasl_passwd": os.path.exists('/etc/postfix/sasl_passwd.lmdb')
                },
                "configuration": {
                    "health_port": int(os.getenv('HEALTH_PORT', '8080'))
                }
            }
            
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(response, indent=2).encode())
            
        except Exception as e:
            logger.error(f"Status check error: {e}")
            self.send_error(500, "Internal Server Error")
    
    def get_health_status(self) -> Dict:
        """Get comprehensive Postfix health status"""
        health_data = {
            'healthy': False,
            'postfix_running': False,
            'queue_healthy': False,
            'config_valid': False
        }
        
        # Check if Postfix is running
        try:
            result = subprocess.run(
                ['postfix', 'status'], 
                capture_output=True, text=True, timeout=10
            )
            health_data['postfix_running'] = result.returncode == 0
        except Exception as e:
            logger.debug(f"Error checking Postfix status: {e}")
        
        # Check Postfix queue health
        try:
            result = subprocess.run(
                ['postqueue', '-p'], 
                capture_output=True, text=True, timeout=10
            )
            # If postqueue returns 0 and output is minimal, queue is healthy
            # Large queues or errors indicate problems
            if result.returncode == 0:
                queue_output = result.stdout.strip()
                # Healthy if queue is empty or has minimal entries
                health_data['queue_healthy'] = (
                    'Mail queue is empty' in queue_output or 
                    len(queue_output.splitlines()) < 50  # Arbitrary threshold
                )
            else:
                health_data['queue_healthy'] = False
        except Exception as e:
            logger.info(f"Error checking Postfix queue: {e}")
            health_data['queue_healthy'] = False
        
        # Check if Postfix configuration is valid
        try:
            result = subprocess.run(
                ['postfix', 'check'], 
                capture_output=True, text=True, timeout=10
            )
            health_data['config_valid'] = result.returncode == 0
        except Exception as e:
            logger.info(f"Error checking Postfix config: {e}")
        
        # Overall health: all critical checks must pass
        health_data['healthy'] = (
            health_data['postfix_running'] and 
            health_data['queue_healthy'] and
            health_data['config_valid']
        )
        
        return health_data
    
    def get_postfix_metrics(self) -> Dict:
        """Get Postfix-specific metrics and information"""
        metrics = {
            'queue_stats': {},
            'process_info': {},
            'config_info': {}
        }
        
        # Get queue statistics
        try:
            result = subprocess.run(
                ['postqueue', '-p'], 
                capture_output=True, text=True, timeout=10
            )
            if result.returncode == 0:
                queue_output = result.stdout.strip()
                if 'Mail queue is empty' in queue_output:
                    metrics['queue_stats'] = {
                        'total_messages': 0,
                        'status': 'empty'
                    }
                else:
                    lines = queue_output.splitlines()
                    metrics['queue_stats'] = {
                        'total_messages': max(0, len(lines) - 2),  # Subtract headers
                        'status': 'has_messages',
                        'sample_output': lines[:5] if lines else []
                    }
        except Exception as e:
            logger.info(f"Error getting queue stats: {e}")
            metrics['queue_stats'] = {'error': str(e)}
        
        # Get Postfix process information
        try:
            result = subprocess.run(
                ['postfix', 'status'], 
                capture_output=True, text=True, timeout=10
            )
            metrics['process_info'] = {
                'status_command_result': result.returncode,
                'status_output': result.stdout.strip() if result.stdout else '',
                'running': result.returncode == 0
            }
        except Exception as e:
            logger.info(f"Error getting process info: {e}")
            metrics['process_info'] = {'error': str(e)}
        
        # Get basic configuration info
        try:
            config_files = {
                'main.cf': '/etc/postfix/main.cf',
                'master.cf': '/etc/postfix/master.cf',
                'clients.cidr': '/etc/postfix/clients.cidr'
            }
            
            for name, path in config_files.items():
                if os.path.exists(path):
                    stat = os.stat(path)
                    metrics['config_info'][name] = {
                        'exists': True,
                        'size': stat.st_size,
                        'modified': datetime.fromtimestamp(stat.st_mtime).isoformat()
                    }
                else:
                    metrics['config_info'][name] = {'exists': False}
                    
        except Exception as e:
            logger.info(f"Error getting config info: {e}")
            metrics['config_info'] = {'error': str(e)}
        
        return metrics
    
    def log_message(self, format, *args):
        """Override to use our logger"""
        logger.info(f"HTTP: {format % args}")

class HealthCheckService:
    """Health check service manager"""
    
    def __init__(self):
        self.port = int(os.getenv('HEALTH_PORT', '8080'))
        self.server = None
        self.server_thread = None
    
    def start(self):
        """Start the health check HTTP server"""
        try:
            self.server = HTTPServer(('0.0.0.0', self.port), HealthCheckHandler)
            
            def run_server():
                logger.info(f"Health check server starting on port {self.port}")
                logger.info(f"Health endpoints available at:")
                logger.info(f"  - http://0.0.0.0:{self.port}/postfix (for Pingdom)")
                logger.info(f"  - http://0.0.0.0:{self.port}/status (detailed info)")
                if self.server:  # Add null check
                    self.server.serve_forever()
            
            self.server_thread = threading.Thread(target=run_server, daemon=True)
            self.server_thread.start()
            
            return True
            
        except Exception as e:
            logger.error(f"Failed to start health check server: {e}")
            self.server = None  # Ensure server is None on failure
            return False
    
    def stop(self):
        """Stop the health check HTTP server"""
        if self.server:
            self.server.shutdown()
            logger.info("Health check server stopped")
    
    def run_forever(self):
        """Keep the service running"""
        logger.info("Starting Postfix Health Check Service")
        
        if not self.start():
            logger.error("Failed to start health check service")
            return 1
        
        try:
            # Keep the main thread alive
            while True:
                time.sleep(60)  # Check every minute
                
                # Log periodic status
                if logger.isEnabledFor(logging.DEBUG):
                    handler = HealthCheckHandler()
                    health_data = handler.get_health_status()
                    logger.info(f"Postfix health status: {health_data['healthy']}")
                    
        except KeyboardInterrupt:
            logger.info("Health check service stopped by user")
        except Exception as e:
            logger.error(f"Health check service error: {e}")
            return 1
        finally:
            self.stop()
        
        return 0

if __name__ == "__main__":
    service = HealthCheckService()
    exit_code = service.run_forever()
    exit(exit_code)
