<?php

use Psr\Http\Message\ResponseInterface as Response;
use Psr\Http\Message\ServerRequestInterface as Request;
require __DIR__ . '/../vendor/autoload.php';

$app = new \Slim\App;

function parse_status(array $status): array
{
    // Split the output into lines
    $client = explode(': ', $status[0])[1];
    $endpoint = explode(': ', $status[1])[1];
    $allowedIps = explode(': ', $status[2])[1];
    $uptime = explode(': ', $status[3])[1];
    $traffic = explode(': ', $status[4])[1];
    $traffic = explode(', ', $traffic);
    $received = explode(' ', $traffic[0]);
    $sent = explode(' ', $traffic[1]);

    return [
      "client" => $client,
      "endpoint" => $endpoint,
      "allowedIps" => $allowedIps,
      "uptime" => $uptime ?? "",
      "traffic" => [
          "received" => [
              "value" => floatval($received[0]),
              "unit" => $received[1] ?? ""
          ],
          "sent" => [
              "value" => floatval($sent[0]),
              "unit" => $sent[1] ?? ""
          ]
      ]
    ];
}

$app->get('/', function (Request $request, Response $response, array $args) {
    $response->getBody()->write(file_get_contents("index.html"));
    return $response;
});

$app->get('/status', function (Request $request, Response $response, array $args) {
    $output = [];
    $exit_code = -1;

    exec("(wg show external | sed -n '/peer/,\$p' | grep ':') 2>&1", $output, $exit_code);

    if($exit_code === 0) {
        $parsed = parse_status($output);
        return $response->withStatus(200)
            ->withHeader('Content-Type', 'application/json')
            ->withJson($parsed);
    } else {
        return $response->withStatus(500)
            ->withHeader('Content-Type', 'application/json')
            ->withJson([
                "errors" => $output
            ]);
    }
});

$app->post('/disconnect', function (Request $request, Response $response, array $args) {
    $output = "";
    $exit_code = -1;

    exec("wg-quick down /external.conf 2>&1", $output, $exit_code);

    if($exit_code === 0) {
        return $response->withStatus(200)
          ->withHeader('Content-Type', 'application/json')
          ->withJson([
            "connected" => false
          ]);
    } else {
        return $response->withStatus(500)
          ->withHeader('Content-Type', 'application/json')
          ->withJson([
            "connected" => true,
            "errors" => $output
          ]);
    }
});

$app->post('/connect', function (Request $request, Response $response, array $args) {
    $output = "";
    $exit_code = -1;

    exec("wg-quick up /external.conf 2>&1", $output, $exit_code);

    if($exit_code === 0) {
        return $response->withStatus(200)
          ->withHeader('Content-Type', 'application/json')
          ->withJson([
            "connected" => true
          ]);
    } else {
        return $response->withStatus(500)
          ->withHeader('Content-Type', 'application/json')
          ->withJson([
            "connected" => false,
            "errors" => $output
          ]);
    }
});

$app->run();