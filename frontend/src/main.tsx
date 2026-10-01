import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { App as AntApp, ConfigProvider } from "antd";
import { BrowserRouter } from "react-router-dom";
import viVN from "antd/locale/vi_VN";
import dayjs from "dayjs";
import "dayjs/locale/vi";
import App from "./App";
import { AuthProvider } from "./auth/AuthProvider";
import "@fontsource/be-vietnam-pro/400.css";
import "@fontsource/be-vietnam-pro/500.css";
import "@fontsource/be-vietnam-pro/600.css";
import "@fontsource/be-vietnam-pro/700.css";
import "@fontsource/montserrat/600.css";
import "@fontsource/montserrat/700.css";
import "@fontsource/montserrat/800.css";
import "./styles.css";

dayjs.locale("vi");

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 30_000, refetchOnWindowFocus: false },
    mutations: { retry: 0 },
  },
});

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ConfigProvider
      locale={viVN}
      theme={{
        token: {
          colorPrimary: "#009edb",
          colorInfo: "#009edb",
          colorSuccess: "#0f9f6e",
          colorWarning: "#f5a623",
          colorError: "#d9363e",
          borderRadius: 8,
          fontFamily: '"Be Vietnam Pro", sans-serif',
          colorText: "#14233c",
          colorTextSecondary: "#51657d",
          colorTextTertiary: "#60738a",
          colorTextQuaternary: "#718196",
          colorTextDescription: "#5b6f87",
          colorTextPlaceholder: "#64778d",
          colorBorder: "#dbe4ee",
          colorBgContainer: "#ffffff",
          controlOutline: "rgba(0, 158, 219, .16)",
        },
        components: {
          Button: { controlHeight: 40, fontWeight: 700, primaryShadow: "0 8px 20px rgba(0, 158, 219, .18)" },
          Input: { controlHeight: 42, activeBorderColor: "#009edb", hoverBorderColor: "#009edb" },
          InputNumber: { controlHeight: 42 },
          Select: { controlHeight: 42, optionSelectedBg: "#e9f7fc" },
          DatePicker: { controlHeight: 42 },
          Card: { headerBg: "transparent", paddingLG: 22 },
          Table: { headerBg: "#f3f8fc", headerColor: "#003070", rowHoverBg: "#f5fbfe" },
          Menu: { itemBorderRadius: 6, itemHeight: 46 },
        },
      }}
    >
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <BrowserRouter>
              <App />
            </BrowserRouter>
          </AuthProvider>
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>
  </React.StrictMode>,
);
