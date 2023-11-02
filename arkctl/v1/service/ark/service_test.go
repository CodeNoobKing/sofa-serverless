package ark

import (
	"context"
	"encoding/json"
	"net"
	"net/http"
	"testing"

	"github.com/sirupsen/logrus"
	"github.com/stretchr/testify/assert"
)

func mockHttpServer(
	path string,
	handler func(w http.ResponseWriter, r *http.Request),
) (int, func()) {
	// Create a listener on a random port.
	listener, err := net.Listen("tcp", ":0")
	if err != nil {
		panic(err)
	}

	mux := http.NewServeMux()

	// Retrieve the port.
	port := listener.Addr().(*net.TCPAddr).Port
	mux.Handle(path, http.HandlerFunc(handler))

	server := &http.Server{
		Handler: mux,
	}

	go func() {
		if err := server.Serve(listener); err != nil {
			logrus.Warn(err)
		}
	}()

	return port, func() {
		listener.Close()
	}
}

func TestInstallBiz_Success(t *testing.T) {
	ctx := context.Background()
	client := BuildClient(ctx)
	port, cancel := mockHttpServer("/installBiz", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    "SUCCESS",
			"message": "install biz success!",
		})
	})
	defer func() {
		cancel()
	}()

	err := client.InstallBiz(ctx, InstallBizRequest{
		BizModel: BizModel{
			BizName:    "biz",
			BizVersion: "0.0.1-SNAPSHOT",
			BizUrl:     "",
		},
		TargetContainer: ArkContainerRuntimeInfo{
			RunType: ArkContainerRunTypeLocal,
			Port:    &port,
		},
	})
	assert.Nil(t, err)

}

func TestInstallBiz_Failed(t *testing.T) {
	ctx := context.Background()
	client := BuildClient(ctx)
	port, cancel := mockHttpServer("/installBiz", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    "FAILED",
			"message": "install biz failed!",
		})
	})
	defer func() {
		cancel()
	}()

	err := client.InstallBiz(ctx, InstallBizRequest{
		BizModel: BizModel{
			BizName:    "biz",
			BizVersion: "0.0.1-SNAPSHOT",
			BizUrl:     "",
		},
		TargetContainer: ArkContainerRuntimeInfo{
			RunType: ArkContainerRunTypeLocal,
			Port:    &port,
		},
	})
	assert.NotNil(t, err)
	assert.Equal(t, "install biz failed: install biz failed!", err.Error())
}

func TestInstallBiz_NoServer(t *testing.T) {
	ctx := context.Background()
	client := BuildClient(ctx)
	port := 8888

	err := client.InstallBiz(ctx, InstallBizRequest{
		BizModel: BizModel{
			BizName:    "biz",
			BizVersion: "0.0.1-SNAPSHOT",
			BizUrl:     "file:///foobar",
		},
		TargetContainer: ArkContainerRuntimeInfo{
			RunType: ArkContainerRunTypeLocal,
			Port:    &port,
		},
	})
	assert.NotNil(t, err)
	assert.Equal(t, "Post \"http://127.0.0.1:8888/installBiz\": dial tcp 127.0.0.1:8888: connect: connection refused", err.Error())
}

func TestUnInstallBiz_Success(t *testing.T) {
	ctx := context.Background()
	client := BuildClient(ctx)
	port, cancel := mockHttpServer("/uninstallBiz", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    "SUCCESS",
			"message": "uninstall biz success!",
		})
	})
	defer func() {
		cancel()
	}()

	err := client.UnInstallBiz(ctx, UnInstallBizRequest{
		BizModel: BizModel{
			BizName:    "biz",
			BizVersion: "0.0.1-SNAPSHOT",
			BizUrl:     "",
		},
		TargetContainer: ArkContainerRuntimeInfo{
			RunType: ArkContainerRunTypeLocal,
			Port:    &port,
		},
	})
	assert.Nil(t, err)

}

func TestUnInstallBiz_NotInstalled(t *testing.T) {
	ctx := context.Background()
	client := BuildClient(ctx)
	port, cancel := mockHttpServer("/uninstallBiz", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    "FAILED",
			"message": "uninstall biz success!",
			"data": map[string]interface{}{
				"code": "NOT_FOUND_BIZ",
			},
		})
	})
	defer func() {
		cancel()
	}()

	err := client.UnInstallBiz(ctx, UnInstallBizRequest{
		BizModel: BizModel{
			BizName:    "biz",
			BizVersion: "0.0.1-SNAPSHOT",
			BizUrl:     "",
		},
		TargetContainer: ArkContainerRuntimeInfo{
			RunType: ArkContainerRunTypeLocal,
			Port:    &port,
		},
	})
	assert.Nil(t, err)

}

func TestUnInstallBiz_Failed(t *testing.T) {
	ctx := context.Background()
	client := BuildClient(ctx)
	port, cancel := mockHttpServer("/uninstallBiz", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    "FAILED",
			"message": "uninstall biz success!",
			"data": map[string]interface{}{
				"code": "FOO",
			},
		})
	})
	defer func() {
		cancel()
	}()

	err := client.UnInstallBiz(ctx, UnInstallBizRequest{
		BizModel: BizModel{
			BizName:    "biz",
			BizVersion: "0.0.1-SNAPSHOT",
			BizUrl:     "",
		},
		TargetContainer: ArkContainerRuntimeInfo{
			RunType: ArkContainerRunTypeLocal,
			Port:    &port,
		},
	})
	assert.NotNil(t, err)
	assert.Equal(t, "uninstall biz failed: {{FAILED {FOO  0 []} uninstall biz success!}}", err.Error())

}

func TestUnInstallBiz_NoServer(t *testing.T) {
	ctx := context.Background()
	client := BuildClient(ctx)
	port := 8888

	err := client.UnInstallBiz(ctx, UnInstallBizRequest{
		BizModel: BizModel{
			BizName:    "biz",
			BizVersion: "0.0.1-SNAPSHOT",
			BizUrl:     "",
		},
		TargetContainer: ArkContainerRuntimeInfo{
			RunType: ArkContainerRunTypeLocal,
			Port:    &port,
		},
	})
	assert.NotNil(t, err)
	assert.Equal(t, "Post \"http://127.0.0.1:8888/uninstallBiz\": dial tcp 127.0.0.1:8888: connect: connection refused", err.Error())

}