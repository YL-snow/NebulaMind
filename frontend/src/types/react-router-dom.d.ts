// Type declarations for react-router-dom v7
// (npm 安装时类型文件缺失的临时补丁)
declare module 'react-router-dom' {
  import React from 'react';

  // Router components
  export class BrowserRouter extends React.Component<any, any> {}
  export class HashRouter extends React.Component<any, any> {}
  export class MemoryRouter extends React.Component<any, any> {}
  export class Router extends React.Component<any, any> {}
  export class StaticRouter extends React.Component<any, any> {}
  export class Link extends React.Component<any, any> {}
  export class NavLink extends React.Component<any, any> {}
  export class Navigate extends React.Component<any, any> {}
  export class Outlet extends React.Component<any, any> {}
  export class Route extends React.Component<any, any> {}
  export class Routes extends React.Component<any, any> {}
  export class RouterProvider extends React.Component<any, any> {}
  export class HydratedRouter extends React.Component<any, any> {}

  // Hooks
  export function useHref(to: any): string;
  export function useLocation(): any;
  export function useMatch(pattern: any): any;
  export function useNavigate(): any;
  export function useNavigation(): any;
  export function useParams<P = any>(): P;
  export function useResolvedPath(to: any): any;
  export function useSearchParams(): [URLSearchParams, any];
  export function useFetcher(): any;
  export function useRouteError(): any;
  export function useRouteLoaderData(routeId: string): any;
  export function useLoaderData(): any;
  export function useActionData(): any;
  export function useMatches(): any;
  export function useBeforeUnload(callback: any): void;
  export function useBlocker(blocker: any): void;

  // Router creation
  export function createBrowserRouter(routes: any, opts?: any): any;
  export function createHashRouter(routes: any, opts?: any): any;
  export function createMemoryRouter(routes: any, opts?: any): any;
  export function createRoutesFromElements(children: any): any;
  export function createRoutesFromChildren(children: any): any;
  export function renderMatches(matches: any): any;

  // Components
  export interface FormProps {
    children?: React.ReactNode;
    action?: string;
    method?: string;
    onSubmit?: (event: React.FormEvent<HTMLFormElement>) => void;
    [key: string]: any;
  }
  export class Form extends React.Component<FormProps, any> {}

  // Data types
  export type ActionFunction = (args: any) => any;
  export type LoaderFunction = (args: any) => any;
  export type ShouldRevalidateFunction = (args: any) => boolean;
  export interface ActionFunctionArgs { request: Request; params: any; context?: any; }
  export interface LoaderFunctionArgs { request: Request; params: any; context?: any; }
}
